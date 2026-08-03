package com.archops.graph.semantics;

import com.archops.asset.domain.Asset;
import com.archops.asset.repository.AssetRepository;
import com.archops.common.exception.BusinessException;
import com.archops.knowledge.architecture.domain.ArchitectureFact;
import com.archops.knowledge.architecture.domain.ArchitecturePartition;
import com.archops.knowledge.architecture.domain.ArchitectureRevision;
import com.archops.knowledge.architecture.repository.ArchitectureFactRepository;
import com.archops.knowledge.architecture.repository.ArchitecturePartitionRepository;
import com.archops.knowledge.architecture.repository.ArchitectureRevisionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scans free-text side channels for topology prose and suggests typed GraphOps.
 */
@Service
public class TopologyProseAuditService {

    private static final Pattern ASSET_ID = Pattern.compile("(?i)\\b(?:asset[#:=\\s]|id[#:=\\s])(\\d{1,18})\\b");
    private static final Pattern HOST_PAIR = Pattern.compile(
            "(?i)(?:via|jump|bastion|跳板)\\s*[:=]?\\s*([a-z0-9._-]{2,64})");

    private final AssetRepository assetRepository;
    private final ArchitectureFactRepository factRepository;
    private final ArchitecturePartitionRepository partitionRepository;
    private final ArchitectureRevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;

    public TopologyProseAuditService(
            AssetRepository assetRepository,
            ArchitectureFactRepository factRepository,
            ArchitecturePartitionRepository partitionRepository,
            ArchitectureRevisionRepository revisionRepository,
            ObjectMapper objectMapper) {
        this.assetRepository = assetRepository;
        this.factRepository = factRepository;
        this.partitionRepository = partitionRepository;
        this.revisionRepository = revisionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public TopologyProseAuditResponse audit() {
        List<TopologyProseAuditResponse.Finding> findings = new ArrayList<>();
        List<Map<String, Object>> suggestedOps = new ArrayList<>();
        Map<String, Asset> byHost = indexByHost();
        Map<Long, Asset> byId = indexById();

        for (Asset asset : assetRepository.findByDeletedAtIsNull()) {
            String description = readDescription(asset.getMetadata());
            TopologyProseDetector.Result result = TopologyProseDetector.scan(description);
            if (result.level() == TopologyProseDetector.Level.NONE) {
                continue;
            }
            findings.add(new TopologyProseAuditResponse.Finding(
                    "ASSET_DESCRIPTION",
                    asset.getId(),
                    asset.getElementId() != null ? asset.getElementId().toString() : null,
                    asset.getName(),
                    result.level().name(),
                    description,
                    result.hits().stream().map(TopologyProseDetector.Hit::excerpt).toList()));
            suggestedOps.addAll(suggestFromText(
                    description, asset, byHost, byId, result.level() == TopologyProseDetector.Level.HARD));
        }

        for (ArchitectureFact fact : factRepository.findAll()) {
            if (fact.getStatus() != null && !"active".equalsIgnoreCase(fact.getStatus())) {
                continue;
            }
            String blob = String.join(
                    " ",
                    nullToEmpty(fact.getFactType()),
                    nullToEmpty(fact.getSubject()),
                    nullToEmpty(fact.getPredicate()),
                    nullToEmpty(fact.getObject()));
            boolean edgePred = TopologyProseDetector.isTopologyEdgePredicate(fact.getPredicate());
            TopologyProseDetector.Result result = TopologyProseDetector.scan(blob);
            if (!edgePred && result.level() == TopologyProseDetector.Level.NONE) {
                continue;
            }
            TopologyProseDetector.Level level = edgePred ? TopologyProseDetector.Level.HARD : result.level();
            findings.add(new TopologyProseAuditResponse.Finding(
                    "ARCHITECTURE_FACT",
                    fact.getId(),
                    null,
                    fact.getSubject() + " " + fact.getPredicate() + " " + fact.getObject(),
                    level.name(),
                    blob,
                    edgePred
                            ? List.of("predicate=" + fact.getPredicate())
                            : result.hits().stream().map(TopologyProseDetector.Hit::excerpt).toList()));
            if (edgePred) {
                Map<String, Object> op = suggestFactEdge(fact, byId);
                if (op != null) {
                    suggestedOps.add(op);
                }
            }
        }

        for (ArchitecturePartition partition : partitionRepository.findAll()) {
            ArchitectureRevision revision = revisionRepository
                    .findTopByPartitionIdOrderByVersionDesc(partition.getId())
                    .orElse(null);
            if (revision == null || revision.getBodyMd() == null || revision.getBodyMd().isBlank()) {
                continue;
            }
            TopologyProseDetector.Result result = TopologyProseDetector.scan(revision.getBodyMd());
            if (result.level() == TopologyProseDetector.Level.NONE) {
                continue;
            }
            findings.add(new TopologyProseAuditResponse.Finding(
                    "PARTITION_BODY_MD",
                    revision.getId(),
                    partition.getPartitionKey(),
                    partition.getTitle(),
                    result.level().name(),
                    truncate(revision.getBodyMd(), 400),
                    result.hits().stream().map(TopologyProseDetector.Hit::excerpt).toList()));
        }

        int hard = 0;
        int warn = 0;
        for (TopologyProseAuditResponse.Finding f : findings) {
            if ("HARD".equals(f.level())) {
                hard++;
            } else {
                warn++;
            }
        }
        return new TopologyProseAuditResponse(findings.size(), hard, warn, findings, suggestedOps);
    }

    private List<Map<String, Object>> suggestFromText(
            String text,
            Asset source,
            Map<String, Asset> byHost,
            Map<Long, Asset> byId,
            boolean preferConnectsVia) {
        List<Map<String, Object>> ops = new ArrayList<>();
        if (source.getElementId() == null) {
            return ops;
        }
        Matcher ids = ASSET_ID.matcher(text);
        while (ids.find()) {
            try {
                long id = Long.parseLong(ids.group(1));
                Asset target = byId.get(id);
                if (target == null || target.getElementId() == null || target.getId().equals(source.getId())) {
                    continue;
                }
                String type = preferConnectsVia
                                && source.getKind() != null
                                && source.getKind().name().equals("SERVER")
                                && target.getKind() != null
                                && target.getKind().name().equals("SERVER")
                        ? "CONNECTS_VIA"
                        : "DEPENDS_ON";
                ops.add(relCreate(type, source.getElementId(), target.getElementId(),
                        "audit from asset description #" + source.getId()));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        Matcher hosts = HOST_PAIR.matcher(text);
        while (hosts.find()) {
            Asset jump = byHost.get(hosts.group(1).toLowerCase(Locale.ROOT));
            if (jump == null || jump.getElementId() == null || jump.getId().equals(source.getId())) {
                continue;
            }
            if (source.getKind() != null
                    && "SERVER".equals(source.getKind().name())
                    && jump.getKind() != null
                    && "SERVER".equals(jump.getKind().name())) {
                ops.add(relCreate(
                        "CONNECTS_VIA",
                        source.getElementId(),
                        jump.getElementId(),
                        "audit jump host from description #" + source.getId()));
            }
        }
        return ops;
    }

    private Map<String, Object> suggestFactEdge(ArchitectureFact fact, Map<Long, Asset> byId) {
        if (fact.getAssetId() == null) {
            return null;
        }
        Asset from = byId.get(fact.getAssetId());
        if (from == null || from.getElementId() == null) {
            return null;
        }
        String pred = fact.getPredicate() != null
                ? fact.getPredicate().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')
                : "";
        String type = switch (pred) {
            case "CONNECTS_VIA", "JUMP", "JUMP_VIA", "BASTION", "跳板" -> "CONNECTS_VIA";
            case "RUNS_ON", "运行在" -> "RUNS_ON";
            case "MEMBER_OF", "属于" -> "MEMBER_OF";
            case "HAS_TAG" -> "HAS_TAG";
            default -> "DEPENDS_ON";
        };
        Long toId = null;
        Matcher m = ASSET_ID.matcher(nullToEmpty(fact.getObject()));
        if (m.find()) {
            try {
                toId = Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        if (toId == null) {
            return null;
        }
        Asset to = byId.get(toId);
        if (to == null || to.getElementId() == null) {
            return null;
        }
        try {
            GraphRelEndpointRules.validate(
                    com.archops.graph.domain.GraphRelType.from(type), from.getKind(), to.getKind());
        } catch (BusinessException ex) {
            return null;
        }
        return relCreate(type, from.getElementId(), to.getElementId(), "audit from fact #" + fact.getId());
    }

    private static Map<String, Object> relCreate(String type, UUID from, UUID to, String description) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("elementId", UUID.randomUUID().toString());
        props.put("description", description);
        if ("CONNECTS_VIA".equals(type)) {
            props.put("order", 0);
            props.put("protocol", "ssh");
        }
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("op", "REL_CREATE");
        op.put("type", type);
        op.put("from", Map.of("elementId", from.toString()));
        op.put("to", Map.of("elementId", to.toString()));
        op.put("properties", props);
        return op;
    }

    private Map<String, Asset> indexByHost() {
        Map<String, Asset> map = new LinkedHashMap<>();
        for (Asset asset : assetRepository.findByDeletedAtIsNull()) {
            if (asset.getHost() != null && !asset.getHost().isBlank()) {
                map.putIfAbsent(asset.getHost().trim().toLowerCase(Locale.ROOT), asset);
            }
        }
        return map;
    }

    private Map<Long, Asset> indexById() {
        Map<Long, Asset> map = new LinkedHashMap<>();
        for (Asset asset : assetRepository.findByDeletedAtIsNull()) {
            map.put(asset.getId(), asset);
        }
        return map;
    }

    private String readDescription(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            JsonNode description = node.get("description");
            return description != null && !description.isNull() ? description.asText(null) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        String text = value.trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    public record TopologyProseAuditResponse(
            int findingCount,
            int hardCount,
            int warnCount,
            List<Finding> findings,
            List<Map<String, Object>> suggestedOps) {

        public record Finding(
                String source,
                Long sourceId,
                String sourceKey,
                String title,
                String level,
                String excerpt,
                List<String> hits) {}
    }
}
