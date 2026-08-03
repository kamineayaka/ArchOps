package com.archops.graph.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.AssetKind;
import com.archops.asset.repository.AssetRepository;
import com.archops.common.exception.BusinessException;
import com.archops.graph.changeset.GraphChangeSet.GraphOp;
import com.archops.graph.changeset.GraphChangeSet.GraphRef;
import com.archops.graph.domain.GraphLabels;
import com.archops.graph.domain.GraphRelType;
import com.archops.graph.semantics.GraphRelEndpointRules;
import com.archops.knowledge.architecture.PartitionKeys;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Applies GraphOp[] inside an open Neo4j transaction. */
@Component
public class GraphOpApplier {

    private final AssetRepository assetRepository;

    public GraphOpApplier(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public GraphApplyJournal applyAll(Session session, List<GraphOp> ops, GraphTempBinder binder, Long proposalId) {
        return session.executeWrite(tx -> {
            GraphApplyJournal journal = new GraphApplyJournal();
            for (GraphOp op : ops) {
                applyOne(tx, op, binder, proposalId, journal);
            }
            return journal;
        });
    }

    /** Reverse committed Neo4j changes from their captured before-images. */
    public void reverseAll(Session session, GraphApplyJournal journal) {
        if (journal == null) {
            return;
        }
        session.executeWrite(tx -> {
            journal.reverse(tx);
            return null;
        });
    }

    /**
     * Legacy best-effort compensation for callers without a journal.
     *
     * @deprecated use {@link #reverseAll(Session, GraphApplyJournal)}
     */
    @Deprecated
    public void reverseAll(Session session, List<GraphOp> ops, GraphTempBinder binder) {
        List<GraphOp> reversed = new ArrayList<>(ops);
        java.util.Collections.reverse(reversed);
        session.executeWrite(tx -> {
            for (GraphOp op : reversed) {
                try {
                    reverseOne(tx, op, binder);
                } catch (Exception ignored) {
                    // compensation is best-effort
                }
            }
            return null;
        });
    }

    private void applyOne(
            TransactionContext tx,
            GraphOp op,
            GraphTempBinder binder,
            Long proposalId,
            GraphApplyJournal journal) {
        String kind = op.op().trim().toUpperCase(Locale.ROOT);
        switch (kind) {
            case "NODE_CREATE" -> applyNodeCreate(tx, op, binder, journal);
            case "NODE_UPDATE" -> applyNodeUpdate(tx, op, binder, journal);
            case "NODE_SOFT_DELETE" -> applyNodeSoftDelete(tx, op, binder, journal);
            case "REL_CREATE" -> applyRelCreate(tx, op, binder, proposalId, journal);
            case "REL_UPDATE" -> applyRelUpdate(tx, op, binder, journal);
            case "REL_DELETE" -> applyRelDelete(tx, op, binder, journal);
            case "TAG_ADD" -> applyTagAdd(tx, op, binder, proposalId, journal);
            case "TAG_REMOVE" -> applyTagRemove(tx, op, binder, journal);
            default -> throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "GRAPH_OP_UNKNOWN", "未知 GraphOp: " + op.op());
        }
    }

    private void reverseOne(TransactionContext tx, GraphOp op, GraphTempBinder binder) {
        String kind = op.op().trim().toUpperCase(Locale.ROOT);
        switch (kind) {
            case "NODE_CREATE" -> {
                UUID elementId = resolveCreatedElementId(op, binder);
                tx.run(
                        "MATCH (n:Asset {elementId: $elementId}) DETACH DELETE n",
                        Values.parameters("elementId", elementId.toString()));
            }
            case "REL_CREATE" -> {
                String relId = relElementId(op);
                if (relId != null) {
                    tx.run(
                            "MATCH ()-[r {elementId: $elementId}]->() DELETE r",
                            Values.parameters("elementId", relId));
                }
            }
            default -> {
                // No safe reversal is possible without a before-image.
            }
        }
    }

    private void applyNodeCreate(
            TransactionContext tx, GraphOp op, GraphTempBinder binder, GraphApplyJournal journal) {
        Map<String, Object> props = op.properties() != null ? new LinkedHashMap<>(op.properties()) : new LinkedHashMap<>();
        AssetKind kind = parseKind(props.get("kind"), op.labels());
        UUID elementId;
        Long pgAssetId;
        if (op.tempId() != null && !op.tempId().isBlank()) {
            GraphTempBinder.Binding binding = binder.requireTemp(op.tempId());
            elementId = binding.elementId();
            pgAssetId = binding.pgAssetId();
        } else {
            elementId = props.get("elementId") != null
                    ? UUID.fromString(String.valueOf(props.get("elementId")))
                    : UUID.randomUUID();
            Asset asset = assetRepository.findByElementId(elementId)
                    .orElseThrow(() -> new BusinessException(
                            HttpStatus.BAD_REQUEST,
                            "ASSET_ANCHOR_MISSING",
                            "NODE_CREATE 缺少 PG 锚点: " + elementId));
            pgAssetId = asset.getId();
            binder.remember(elementId, pgAssetId);
        }

        List<String> labels = GraphLabels.normalize(op.labels(), kind);
        Map<String, Object> nodeProps = baseNodeProps(props, kind, elementId, pgAssetId);
        String labelSuffix = GraphLabels.cypherLabelSuffix(labels);
        String cypher = "CREATE (n" + labelSuffix + ") SET n += $props";
        tx.run(cypher, Values.parameters("props", nodeProps)).consume();
        journal.recordNodeCreate(elementId);
    }

    private void applyNodeUpdate(
            TransactionContext tx, GraphOp op, GraphTempBinder binder, GraphApplyJournal journal) {
        UUID elementId = binder.resolveElementId(op.ref());
        assertNodeActive(tx, elementId);

        Map<String, Object> set = sanitizeProps(op.set());
        List<String> unset = mutablePropertyKeys(op.unset(), true);
        Set<String> beforeKeys = new LinkedHashSet<>(set.keySet());
        Set<String> setKeys = new LinkedHashSet<>(set.keySet());
        if (!set.isEmpty()) {
            beforeKeys.add("updatedAt");
            setKeys.add("updatedAt");
        }
        beforeKeys.addAll(unset);
        PropertyBeforeImage before = readNodeProperties(tx, elementId, beforeKeys, setKeys);
        journal.recordNodeUpdate(elementId, before.previousProps(), before.newlySetKeys());

        if (!set.isEmpty()) {
            tx.run(
                    """
                    MATCH (n:Asset {elementId: $elementId})
                    SET n += $props, n.updatedAt = $updatedAt
                    """,
                    Values.parameters(
                            "elementId", elementId.toString(),
                            "props", set,
                            "updatedAt", LocalDateTime.now(ZoneOffset.UTC)));
        }
        for (String key : unset) {
            tx.run(
                        "MATCH (n:Asset {elementId: $elementId}) REMOVE n." + key,
                        Values.parameters("elementId", elementId.toString()));
        }
        if (op.addLabels() != null) {
            for (String label : op.addLabels()) {
                String matched = matchSpecializationLabel(label);
                if ("Asset".equals(matched)) {
                    continue;
                }
                tx.run(
                        "MATCH (n:Asset {elementId: $elementId}) SET n:" + matched,
                        Values.parameters("elementId", elementId.toString()));
            }
        }
        if (op.removeLabels() != null) {
            for (String label : op.removeLabels()) {
                String matched = matchSpecializationLabel(label);
                if ("Asset".equals(matched)) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST, "GRAPH_LABEL_INVALID", "不能移除 Asset label");
                }
                tx.run(
                        "MATCH (n:Asset {elementId: $elementId}) REMOVE n:" + matched,
                        Values.parameters("elementId", elementId.toString()));
            }
        }
    }

    private void applyNodeSoftDelete(
            TransactionContext tx, GraphOp op, GraphTempBinder binder, GraphApplyJournal journal) {
        UUID elementId = binder.resolveElementId(op.ref());
        var nodeResult = tx.run(
                """
                MATCH (n:Asset {elementId: $elementId})
                RETURN coalesce(n.enabled, true) AS enabled
                """,
                Values.parameters("elementId", elementId.toString()));
        if (!nodeResult.hasNext()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GRAPH_NODE_NOT_FOUND", "图节点不存在: " + elementId);
        }
        boolean previousEnabled = nodeResult.single().get("enabled").asBoolean();
        List<GraphApplyJournal.RelLifecycle> touchedRels = new ArrayList<>();
        var relResult = tx.run(
                """
                MATCH (n:Asset {elementId: $elementId})-[r]-()
                WHERE coalesce(r.deleted, false) = false
                RETURN r.elementId AS elementId,
                       coalesce(r.deleted, false) AS deleted,
                       r.deletedAt AS deletedAt
                """,
                Values.parameters("elementId", elementId.toString()));
        while (relResult.hasNext()) {
            var row = relResult.next();
            if (row.get("elementId").isNull()) {
                continue;
            }
            touchedRels.add(new GraphApplyJournal.RelLifecycle(
                    row.get("elementId").asString(),
                    row.get("deleted").asBoolean(),
                    row.get("deletedAt").isNull() ? null : row.get("deletedAt").asObject()));
        }
        journal.recordNodeSoftDelete(elementId, previousEnabled, touchedRels);

        tx.run(
                """
                MATCH (n:Asset {elementId: $elementId})
                SET n.deleted = true,
                    n.deletedAt = $deletedAt,
                    n.enabled = false,
                    n.updatedAt = $deletedAt
                WITH n
                OPTIONAL MATCH (n)-[r]-()
                SET r.deleted = true, r.deletedAt = $deletedAt
                """,
                Values.parameters(
                        "elementId", elementId.toString(),
                        "deletedAt", LocalDateTime.now(ZoneOffset.UTC)));
    }

    private void applyRelCreate(
            TransactionContext tx,
            GraphOp op,
            GraphTempBinder binder,
            Long proposalId,
            GraphApplyJournal journal) {
        if (op.type() == null || op.type().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRAPH_REL_TYPE_REQUIRED", "REL_CREATE 需要 type");
        }
        GraphRelType type = GraphRelType.from(op.type());
        UUID fromId = binder.resolveElementId(op.from());
        UUID toId = binder.resolveElementId(op.to());
        assertNodeActive(tx, fromId);
        assertNodeActive(tx, toId);
        assertRelEndpoints(tx, type, fromId, toId);

        Map<String, Object> props = sanitizeProps(op.properties());
        String elementId = op.properties() != null && op.properties().get("elementId") != null
                ? String.valueOf(op.properties().get("elementId"))
                : UUID.randomUUID().toString();
        props.put("elementId", elementId);
        props.put("deleted", false);
        props.put("createdAt", LocalDateTime.now(ZoneOffset.UTC));
        props.put("updatedAt", LocalDateTime.now(ZoneOffset.UTC));
        if (proposalId != null) {
            props.put("proposalId", proposalId);
        }
        if (type == GraphRelType.CONNECTS_VIA && !props.containsKey("order")) {
            props.put("order", 0);
        }
        if (type == GraphRelType.CONNECTS_VIA && !props.containsKey("protocol")) {
            props.put("protocol", "ssh");
        }

        String cypher = """
                MATCH (a:Asset {elementId: $fromId}), (b:Asset {elementId: $toId})
                CREATE (a)-[r:%s]->(b)
                SET r += $props
                """.formatted(type.name());
        tx.run(cypher, Values.parameters(
                "fromId", fromId.toString(),
                "toId", toId.toString(),
                "props", props)).consume();
        journal.recordRelCreate(elementId);
    }

    private void applyRelUpdate(
            TransactionContext tx, GraphOp op, GraphTempBinder binder, GraphApplyJournal journal) {
        String elementId = resolveRelElementId(op);
        Map<String, Object> set = sanitizeProps(op.set());
        List<String> unset = mutablePropertyKeys(op.unset(), false);
        Set<String> beforeKeys = new LinkedHashSet<>(set.keySet());
        Set<String> setKeys = new LinkedHashSet<>(set.keySet());
        if (!set.isEmpty()) {
            beforeKeys.add("updatedAt");
            setKeys.add("updatedAt");
        }
        beforeKeys.addAll(unset);
        PropertyBeforeImage before = readRelProperties(tx, elementId, beforeKeys, setKeys);
        journal.recordRelUpdate(elementId, before.previousProps(), before.newlySetKeys());

        if (!set.isEmpty()) {
            tx.run(
                    """
                    MATCH ()-[r {elementId: $elementId}]->()
                    WHERE coalesce(r.deleted, false) = false
                    SET r += $props, r.updatedAt = $updatedAt
                    """,
                    Values.parameters(
                            "elementId", elementId,
                            "props", set,
                            "updatedAt", LocalDateTime.now(ZoneOffset.UTC)));
        }
        for (String key : unset) {
            tx.run(
                    "MATCH ()-[r {elementId: $elementId}]->() REMOVE r." + key,
                    Values.parameters("elementId", elementId));
        }
    }

    private void applyRelDelete(
            TransactionContext tx, GraphOp op, GraphTempBinder binder, GraphApplyJournal journal) {
        String elementId = resolveRelElementId(op);
        boolean soft = op.soft() == null || Boolean.TRUE.equals(op.soft());
        if (soft) {
            Set<String> lifecycleKeys = Set.of("deleted", "deletedAt", "updatedAt");
            PropertyBeforeImage before = readRelProperties(tx, elementId, lifecycleKeys, lifecycleKeys);
            journal.recordRelUpdate(elementId, before.previousProps(), before.newlySetKeys());
            tx.run(
                    """
                    MATCH ()-[r {elementId: $elementId}]->()
                    SET r.deleted = true, r.deletedAt = $deletedAt, r.updatedAt = $deletedAt
                    """,
                    Values.parameters(
                            "elementId", elementId,
                            "deletedAt", LocalDateTime.now(ZoneOffset.UTC)));
        } else {
            var result = tx.run(
                    """
                    MATCH (a:Asset)-[r {elementId: $elementId}]->(b:Asset)
                    RETURN type(r) AS type,
                           a.elementId AS fromId,
                           b.elementId AS toId,
                           properties(r) AS props
                    """,
                    Values.parameters("elementId", elementId));
            RelBeforeImage before = null;
            if (result.hasNext()) {
                var row = result.single();
                before = new RelBeforeImage(
                        row.get("type").asString(),
                        UUID.fromString(row.get("fromId").asString()),
                        UUID.fromString(row.get("toId").asString()),
                        row.get("props").asMap());
            }
            tx.run(
                    "MATCH ()-[r {elementId: $elementId}]->() DELETE r",
                    Values.parameters("elementId", elementId)).consume();
            if (before != null) {
                journal.recordRelDelete(
                        elementId, before.type(), before.fromId(), before.toId(), before.props());
            }
        }
    }

    private void applyTagAdd(
            TransactionContext tx,
            GraphOp op,
            GraphTempBinder binder,
            Long proposalId,
            GraphApplyJournal journal) {
        UUID assetId = binder.resolveElementId(op.ref());
        UUID tagId = resolveTagElementId(tx, op, binder);
        GraphOp rel = new GraphOp(
                "REL_CREATE",
                op.opId(),
                op.tempId(),
                null,
                Map.of("elementId", UUID.randomUUID().toString()),
                null,
                new GraphRef(assetId.toString(), null, null),
                new GraphRef(tagId.toString(), null, null),
                GraphRelType.HAS_TAG.name(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                op.riskHint());
        applyRelCreate(tx, rel, binder, proposalId, journal);
    }

    private void applyTagRemove(
            TransactionContext tx, GraphOp op, GraphTempBinder binder, GraphApplyJournal journal) {
        UUID assetId = binder.resolveElementId(op.ref());
        UUID tagId = resolveTagElementId(tx, op, binder);
        var beforeResult = tx.run(
                """
                MATCH (a:Asset {elementId: $assetId})-[r:HAS_TAG]->(t:Asset:Tag {elementId: $tagId})
                WHERE coalesce(r.deleted, false) = false
                RETURN r.elementId AS elementId, properties(r) AS props
                """,
                Values.parameters("assetId", assetId.toString(), "tagId", tagId.toString()));
        while (beforeResult.hasNext()) {
            var row = beforeResult.next();
            if (row.get("elementId").isNull()) {
                continue;
            }
            String relId = row.get("elementId").asString();
            Map<String, Object> props = row.get("props").asMap();
            List<String> newlySet = new ArrayList<>();
            for (String key : List.of("deleted", "deletedAt")) {
                if (!props.containsKey(key)) {
                    newlySet.add(key);
                }
            }
            Map<String, Object> previous = new LinkedHashMap<>();
            props.forEach((key, value) -> {
                if ("deleted".equals(key) || "deletedAt".equals(key)) {
                    previous.put(key, value);
                }
            });
            journal.recordRelUpdate(relId, previous, newlySet);
        }
        tx.run(
                """
                MATCH (a:Asset {elementId: $assetId})-[r:HAS_TAG]->(t:Asset:Tag {elementId: $tagId})
                WHERE coalesce(r.deleted, false) = false
                SET r.deleted = true, r.deletedAt = $deletedAt
                """,
                Values.parameters(
                        "assetId", assetId.toString(),
                        "tagId", tagId.toString(),
                        "deletedAt", LocalDateTime.now(ZoneOffset.UTC)));
    }

    private PropertyBeforeImage readNodeProperties(
            TransactionContext tx, UUID elementId, Set<String> keys, Set<String> setKeys) {
        var result = tx.run(
                "MATCH (n:Asset {elementId: $elementId}) RETURN properties(n) AS props",
                Values.parameters("elementId", elementId.toString()));
        if (!result.hasNext()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GRAPH_NODE_NOT_FOUND", "图节点不存在: " + elementId);
        }
        return propertyBeforeImage(result.single().get("props").asMap(), keys, setKeys);
    }

    private PropertyBeforeImage readRelProperties(
            TransactionContext tx, String elementId, Set<String> keys, Set<String> setKeys) {
        var result = tx.run(
                "MATCH ()-[r {elementId: $elementId}]->() RETURN properties(r) AS props",
                Values.parameters("elementId", elementId));
        if (!result.hasNext()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GRAPH_REL_NOT_FOUND", "图关系不存在: " + elementId);
        }
        return propertyBeforeImage(result.single().get("props").asMap(), keys, setKeys);
    }

    private static PropertyBeforeImage propertyBeforeImage(
            Map<String, Object> props, Set<String> keys, Set<String> setKeys) {
        Map<String, Object> previous = new LinkedHashMap<>();
        List<String> newlySet = new ArrayList<>();
        for (String key : keys) {
            if (props.containsKey(key)) {
                previous.put(key, props.get(key));
            } else if (setKeys.contains(key)) {
                newlySet.add(key);
            }
        }
        return new PropertyBeforeImage(previous, newlySet);
    }

    private static List<String> mutablePropertyKeys(List<String> raw, boolean node) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(GraphOpApplier::isAllowedPropKey)
                .filter(key -> node ? !isIdentityOrProtectedProp(key) : !"elementId".equals(key))
                .distinct()
                .toList();
    }

    private record PropertyBeforeImage(Map<String, Object> previousProps, List<String> newlySetKeys) {}

    private record RelBeforeImage(String type, UUID fromId, UUID toId, Map<String, Object> props) {}

    private UUID resolveTagElementId(TransactionContext tx, GraphOp op, GraphTempBinder binder) {
        if (op.tagRef() != null) {
            UUID id = binder.resolveElementId(op.tagRef());
            assertHasLabel(tx, id, "Tag");
            return id;
        }
        String slug = op.tagSlug() != null ? op.tagSlug() : op.tag();
        if (!StringUtils.hasText(slug)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "TAG_REF_REQUIRED", "TAG_ADD/REMOVE 需要 tagRef 或 tagSlug");
        }
        String normalized = PartitionKeys.normalizeSlug(slug);
        var result = tx.run(
                """
                MATCH (t:Asset:Tag {slug: $slug})
                WHERE coalesce(t.deleted, false) = false
                RETURN t.elementId AS elementId
                LIMIT 1
                """,
                Values.parameters("slug", normalized));
        if (!result.hasNext()) {
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "标签不存在: " + normalized + "（请先 NODE_CREATE kind=TAG）");
        }
        return UUID.fromString(result.next().get("elementId").asString());
    }

    private void assertNodeActive(TransactionContext tx, UUID elementId) {
        var result = tx.run(
                """
                MATCH (n:Asset {elementId: $elementId})
                RETURN coalesce(n.deleted, false) AS deleted
                """,
                Values.parameters("elementId", elementId.toString()));
        if (!result.hasNext()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GRAPH_NODE_NOT_FOUND", "图节点不存在: " + elementId);
        }
        if (result.next().get("deleted").asBoolean()) {
            throw new BusinessException(HttpStatus.CONFLICT, "GRAPH_NODE_DELETED", "图节点已软删: " + elementId);
        }
    }

    private void assertHasLabel(TransactionContext tx, UUID elementId, String label) {
        var result = tx.run(
                "MATCH (n:Asset {elementId: $elementId}) WHERE n:" + label + " RETURN 1 AS ok",
                Values.parameters("elementId", elementId.toString()));
        if (!result.hasNext()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "GRAPH_LABEL_MISMATCH", "节点缺少 label :" + label);
        }
    }

    private void assertRelEndpoints(TransactionContext tx, GraphRelType type, UUID fromId, UUID toId) {
        AssetKind fromKind = readNodeKind(tx, fromId);
        AssetKind toKind = readNodeKind(tx, toId);
        GraphRelEndpointRules.validate(type, fromKind, toKind);
    }

    private AssetKind readNodeKind(TransactionContext tx, UUID elementId) {
        var result = tx.run(
                """
                MATCH (n:Asset {elementId: $elementId})
                WHERE coalesce(n.deleted, false) = false
                RETURN n.kind AS kind
                """,
                Values.parameters("elementId", elementId.toString()));
        if (!result.hasNext()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "GRAPH_NODE_NOT_FOUND", "图节点不存在: " + elementId);
        }
        String kind = result.next().get("kind").asString(null);
        AssetKind parsed = GraphRelEndpointRules.parseKind(kind);
        if (parsed == null) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "GRAPH_REL_ENDPOINT_KIND_UNKNOWN", "节点缺少有效 kind: " + elementId);
        }
        return parsed;
    }

    private Map<String, Object> baseNodeProps(
            Map<String, Object> props, AssetKind kind, UUID elementId, Long pgAssetId) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("elementId", elementId.toString());
        node.put("pgAssetId", pgAssetId);
        node.put("kind", kind.name());
        node.put("name", String.valueOf(props.getOrDefault("name", "unnamed")));
        if (props.get("host") != null) {
            node.put("host", String.valueOf(props.get("host")));
        }
        if (props.get("port") != null) {
            node.put("port", props.get("port") instanceof Number n
                    ? n.intValue()
                    : Integer.parseInt(String.valueOf(props.get("port"))));
        }
        node.put("enabled", props.get("enabled") == null || Boolean.TRUE.equals(props.get("enabled")));
        node.put("deleted", false);
        node.put("hasCredential", Boolean.TRUE.equals(props.get("hasCredential")));
        node.put("createdAt", LocalDateTime.now(ZoneOffset.UTC));
        node.put("updatedAt", LocalDateTime.now(ZoneOffset.UTC));
        if (kind == AssetKind.TAG) {
            Object slug = props.get("slug");
            if (slug == null || String.valueOf(slug).isBlank()) {
                slug = PartitionKeys.normalizeSlug(String.valueOf(props.get("name")));
            } else {
                slug = PartitionKeys.normalizeSlug(String.valueOf(slug));
            }
            node.put("slug", String.valueOf(slug));
        }
        Map<String, Object> sanitized = sanitizeProps(props);
        for (Map.Entry<String, Object> e : sanitized.entrySet()) {
            if (!node.containsKey(e.getKey())
                    && !"metadata".equals(e.getKey())
                    && !"elementId".equals(e.getKey())
                    && !"pgAssetId".equals(e.getKey())
                    && !"description".equals(e.getKey())) {
                // Node description is retired; edge.description is the remark surface.
                node.put(e.getKey(), e.getValue());
            }
        }
        return node;
    }

    private Map<String, Object> sanitizeProps(Map<String, Object> raw) {
        Map<String, Object> out = new HashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String key = e.getKey();
            if (!isAllowedPropKey(key) || isIdentityOrProtectedProp(key)) {
                continue;
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("secret") || lower.contains("password") || lower.contains("privatekey")
                    || lower.contains("token") || lower.contains("cipher")) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "GRAPH_SECRET_FORBIDDEN", "图属性禁止包含密文字段: " + key);
            }
            Object value = e.getValue();
            if (value instanceof Map || value instanceof List) {
                // Neo4j props must be primitives / arrays of primitives — skip nested maps
                continue;
            }
            out.put(key, value);
        }
        return out;
    }

    /** Identity / lifecycle fields must not be mutated via NODE_UPDATE / REL_UPDATE set maps. */
    private static boolean isIdentityOrProtectedProp(String key) {
        if (key == null) {
            return true;
        }
        return switch (key) {
            case "elementId",
                    "pgAssetId",
                    "kind",
                    "deleted",
                    "deletedAt",
                    "deletedBy",
                    "createdAt" -> true;
            default -> false;
        };
    }

    private static boolean isAllowedPropKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return key.matches("^[A-Za-z_][A-Za-z0-9_]*$");
    }

    private static AssetKind parseKind(Object kindObj, List<String> labels) {
        if (kindObj != null && StringUtils.hasText(String.valueOf(kindObj))) {
            return AssetKind.valueOf(String.valueOf(kindObj).trim().toUpperCase(Locale.ROOT));
        }
        if (labels != null) {
            for (AssetKind kind : AssetKind.values()) {
                String spec = GraphLabels.specialization(kind);
                for (String label : labels) {
                    if (label != null && spec.equalsIgnoreCase(label)) {
                        return kind;
                    }
                }
            }
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "ASSET_KIND_REQUIRED", "NODE_CREATE 需要 kind");
    }

    private static String matchSpecializationLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRAPH_LABEL_INVALID", "label 不能为空");
        }
        if ("Asset".equalsIgnoreCase(label)) {
            return "Asset";
        }
        for (String allowed : List.of(
                "Server", "Cluster", "Service", "Database", "Network", "Tag", "Environment", "Deleted")) {
            if (allowed.equalsIgnoreCase(label)) {
                return allowed;
            }
        }
        for (AssetKind kind : AssetKind.values()) {
            if (kind.name().equalsIgnoreCase(label)) {
                return GraphLabels.specialization(kind);
            }
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "GRAPH_LABEL_INVALID", "非法 label: " + label);
    }

    private static String resolveRelElementId(GraphOp op) {
        if (op.ref() != null && StringUtils.hasText(op.ref().elementId())) {
            return op.ref().elementId().trim();
        }
        String fromProps = relElementId(op);
        if (fromProps != null) {
            return fromProps;
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "REL_REF_REQUIRED", "边操作需要 ref.elementId");
    }

    private static String relElementId(GraphOp op) {
        if (op.properties() != null && op.properties().get("elementId") != null) {
            return String.valueOf(op.properties().get("elementId"));
        }
        return null;
    }

    private static UUID resolveCreatedElementId(GraphOp op, GraphTempBinder binder) {
        if (op.tempId() != null && !op.tempId().isBlank()) {
            return binder.requireTemp(op.tempId()).elementId();
        }
        if (op.properties() != null && op.properties().get("elementId") != null) {
            return UUID.fromString(String.valueOf(op.properties().get("elementId")));
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "ELEMENT_ID_REQUIRED", "无法解析 NODE_CREATE elementId");
    }
}
