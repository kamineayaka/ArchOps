package com.archops.graph.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.AssetKind;
import com.archops.asset.repository.AssetRepository;
import com.archops.common.exception.BusinessException;
import com.archops.graph.changeset.GraphChangeSet.GraphOp;
import com.archops.graph.changeset.GraphChangeSet.GraphRef;
import com.archops.graph.domain.GraphLabels;
import com.archops.graph.domain.GraphRelType;
import com.archops.knowledge.architecture.PartitionKeys;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    public void applyAll(Session session, List<GraphOp> ops, GraphTempBinder binder, Long proposalId) {
        session.executeWrite(tx -> {
            for (GraphOp op : ops) {
                applyOne(tx, op, binder, proposalId);
            }
            return null;
        });
    }

    /** Best-effort reverse for compensation after Neo4j commit / PG failure. */
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

    private void applyOne(TransactionContext tx, GraphOp op, GraphTempBinder binder, Long proposalId) {
        String kind = op.op().trim().toUpperCase(Locale.ROOT);
        switch (kind) {
            case "NODE_CREATE" -> applyNodeCreate(tx, op, binder);
            case "NODE_UPDATE" -> applyNodeUpdate(tx, op, binder);
            case "NODE_SOFT_DELETE" -> applyNodeSoftDelete(tx, op, binder);
            case "REL_CREATE" -> applyRelCreate(tx, op, binder, proposalId);
            case "REL_UPDATE" -> applyRelUpdate(tx, op, binder);
            case "REL_DELETE" -> applyRelDelete(tx, op, binder);
            case "TAG_ADD" -> applyTagAdd(tx, op, binder, proposalId);
            case "TAG_REMOVE" -> applyTagRemove(tx, op, binder);
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
            case "NODE_SOFT_DELETE" -> {
                UUID elementId = binder.resolveElementId(op.ref());
                tx.run(
                        """
                        MATCH (n:Asset {elementId: $elementId})
                        SET n.deleted = false, n.deletedAt = null, n.enabled = true
                        WITH n
                        OPTIONAL MATCH (n)-[r]-()
                        WHERE r.deleted = true
                        SET r.deleted = false, r.deletedAt = null
                        """,
                        Values.parameters("elementId", elementId.toString()));
            }
            case "REL_CREATE", "TAG_ADD" -> {
                // soft-delete or delete by elementId if present in props
                String relId = relElementId(op);
                if (relId != null) {
                    tx.run(
                            "MATCH ()-[r {elementId: $elementId}]-() DELETE r",
                            Values.parameters("elementId", relId));
                }
            }
            default -> {
                // NODE_UPDATE / REL_UPDATE / REL_DELETE / TAG_REMOVE: skip without before-snapshot
            }
        }
    }

    private void applyNodeCreate(TransactionContext tx, GraphOp op, GraphTempBinder binder) {
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
    }

    private void applyNodeUpdate(TransactionContext tx, GraphOp op, GraphTempBinder binder) {
        UUID elementId = binder.resolveElementId(op.ref());
        assertNodeActive(tx, elementId);

        Map<String, Object> set = sanitizeProps(op.set());
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
        if (op.unset() != null) {
            for (String key : op.unset()) {
                if (!isAllowedPropKey(key) || isIdentityOrProtectedProp(key)) {
                    continue;
                }
                tx.run(
                        "MATCH (n:Asset {elementId: $elementId}) REMOVE n." + key,
                        Values.parameters("elementId", elementId.toString()));
            }
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

    private void applyNodeSoftDelete(TransactionContext tx, GraphOp op, GraphTempBinder binder) {
        UUID elementId = binder.resolveElementId(op.ref());
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

    private void applyRelCreate(TransactionContext tx, GraphOp op, GraphTempBinder binder, Long proposalId) {
        if (op.type() == null || op.type().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "GRAPH_REL_TYPE_REQUIRED", "REL_CREATE 需要 type");
        }
        GraphRelType type = GraphRelType.from(op.type());
        UUID fromId = binder.resolveElementId(op.from());
        UUID toId = binder.resolveElementId(op.to());
        assertNodeActive(tx, fromId);
        assertNodeActive(tx, toId);
        assertRelEndpoints(tx, type, toId);

        Map<String, Object> props = sanitizeProps(op.properties());
        String elementId = props.containsKey("elementId")
                ? String.valueOf(props.get("elementId"))
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
    }

    private void applyRelUpdate(TransactionContext tx, GraphOp op, GraphTempBinder binder) {
        String elementId = resolveRelElementId(op);
        Map<String, Object> set = sanitizeProps(op.set());
        if (!set.isEmpty()) {
            tx.run(
                    """
                    MATCH ()-[r {elementId: $elementId}]-()
                    WHERE coalesce(r.deleted, false) = false
                    SET r += $props, r.updatedAt = $updatedAt
                    """,
                    Values.parameters(
                            "elementId", elementId,
                            "props", set,
                            "updatedAt", LocalDateTime.now(ZoneOffset.UTC)));
        }
        if (op.unset() != null) {
            for (String key : op.unset()) {
                if (!isAllowedPropKey(key) || "elementId".equals(key)) {
                    continue;
                }
                tx.run(
                        "MATCH ()-[r {elementId: $elementId}]-() REMOVE r." + key,
                        Values.parameters("elementId", elementId));
            }
        }
    }

    private void applyRelDelete(TransactionContext tx, GraphOp op, GraphTempBinder binder) {
        String elementId = resolveRelElementId(op);
        boolean soft = op.soft() == null || Boolean.TRUE.equals(op.soft());
        if (soft) {
            tx.run(
                    """
                    MATCH ()-[r {elementId: $elementId}]-()
                    SET r.deleted = true, r.deletedAt = $deletedAt, r.updatedAt = $deletedAt
                    """,
                    Values.parameters(
                            "elementId", elementId,
                            "deletedAt", LocalDateTime.now(ZoneOffset.UTC)));
        } else {
            tx.run(
                    "MATCH ()-[r {elementId: $elementId}]-() DELETE r",
                    Values.parameters("elementId", elementId));
        }
    }

    private void applyTagAdd(TransactionContext tx, GraphOp op, GraphTempBinder binder, Long proposalId) {
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
        applyRelCreate(tx, rel, binder, proposalId);
    }

    private void applyTagRemove(TransactionContext tx, GraphOp op, GraphTempBinder binder) {
        UUID assetId = binder.resolveElementId(op.ref());
        UUID tagId = resolveTagElementId(tx, op, binder);
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

    private void assertRelEndpoints(TransactionContext tx, GraphRelType type, UUID toId) {
        switch (type) {
            case MEMBER_OF -> assertHasLabel(tx, toId, "Cluster");
            case HAS_TAG -> assertHasLabel(tx, toId, "Tag");
            default -> {
            }
        }
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
                    && !"pgAssetId".equals(e.getKey())) {
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
