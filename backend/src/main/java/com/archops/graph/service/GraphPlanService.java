package com.archops.graph.service;

import com.archops.common.exception.BusinessException;
import com.archops.graph.changeset.GraphChangeSet;
import com.archops.graph.domain.GraphRelType;
import com.archops.graph.dto.GraphPlanRequest;
import com.archops.graph.dto.GraphPlanResponse;
import com.archops.knowledge.architecture.PartitionKeys;
import com.archops.knowledge.architecture.service.ArchitecturePartitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GraphPlanService {

    private final GraphVersionService graphVersionService;
    private final ArchitecturePartitionService partitionService;
    private final ObjectMapper objectMapper;

    public GraphPlanService(
            GraphVersionService graphVersionService,
            ArchitecturePartitionService partitionService,
            ObjectMapper objectMapper) {
        this.graphVersionService = graphVersionService;
        this.partitionService = partitionService;
        this.objectMapper = objectMapper;
    }

    public GraphPlanResponse plan(GraphPlanRequest request) {
        List<Map<String, Object>> rawOps = request.ops() != null ? request.ops() : List.of();
        if (rawOps.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PLAN_OPS_EMPTY", "计划 ops 不能为空");
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String risk = "LOW";

        int i = 0;
        for (Map<String, Object> raw : rawOps) {
            i++;
            Map<String, Object> op = new LinkedHashMap<>(raw);
            String kind = str(op.get("op"));
            if (kind == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "GRAPH_OP_INVALID", "ops[" + i + "].op 缺失");
            }
            kind = kind.trim().toUpperCase(Locale.ROOT);
            op.put("op", kind);
            if (op.get("opId") == null) {
                op.put("opId", "op_" + i);
            }

            switch (kind) {
                case "NODE_CREATE" -> {
                    risk = maxRisk(risk, "HIGH");
                    ensureTempId(op, i);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> props = op.get("properties") instanceof Map<?, ?> m
                            ? new LinkedHashMap<>((Map<String, Object>) m)
                            : new LinkedHashMap<>();
                    if (props.get("elementId") == null) {
                        props.put("elementId", UUID.randomUUID().toString());
                    }
                    if (props.get("kind") == null) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST, "ASSET_KIND_REQUIRED", "NODE_CREATE 需要 properties.kind");
                    }
                    if (props.get("name") == null || String.valueOf(props.get("name")).isBlank()) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST, "ASSET_NAME_REQUIRED", "NODE_CREATE 需要 properties.name");
                    }
                    op.put("properties", props);
                    if (op.get("labels") == null) {
                        op.put("labels", List.of("Asset", titleCase(String.valueOf(props.get("kind")))));
                    }
                }
                case "NODE_UPDATE" -> risk = maxRisk(risk, "MEDIUM");
                case "NODE_SOFT_DELETE" -> risk = maxRisk(risk, "CRITICAL");
                case "REL_CREATE" -> {
                    String type = str(op.get("type"));
                    if (type == null) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST, "GRAPH_REL_TYPE_REQUIRED", "REL_CREATE 需要 type");
                    }
                    try {
                        GraphRelType.from(type);
                    } catch (Exception ex) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST, "GRAPH_REL_TYPE_INVALID", "非法边类型: " + type);
                    }
                    op.put("type", type.trim().toUpperCase(Locale.ROOT));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> props = op.get("properties") instanceof Map<?, ?> m
                            ? new LinkedHashMap<>((Map<String, Object>) m)
                            : new LinkedHashMap<>();
                    if (props.get("elementId") == null) {
                        props.put("elementId", UUID.randomUUID().toString());
                    }
                    op.put("properties", props);
                    if ("CONNECTS_VIA".equals(op.get("type"))) {
                        risk = maxRisk(risk, "CRITICAL");
                    } else if ("DEPENDS_ON".equals(op.get("type"))) {
                        risk = maxRisk(risk, "HIGH");
                    } else {
                        risk = maxRisk(risk, "MEDIUM");
                    }
                }
                case "REL_UPDATE" -> risk = maxRisk(risk, "MEDIUM");
                case "REL_DELETE" -> risk = maxRisk(risk, "HIGH");
                case "TAG_ADD" -> risk = maxRisk(risk, "LOW");
                case "TAG_REMOVE" -> risk = maxRisk(risk, "LOW");
                default -> throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "GRAPH_OP_UNKNOWN", "未知 GraphOp: " + kind);
            }
            normalized.add(op);
        }

        long graphVersion = graphVersionService.currentVersion();
        String partitionKey = PartitionKeys.GLOBAL;
        partitionService.getOrCreate(partitionKey);
        long partitionVersion = partitionService.currentVersion(partitionKey);

        String changeSetId = "cs_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> changeSet = new LinkedHashMap<>();
        changeSet.put("schemaVersion", 1);
        changeSet.put("changeSetId", changeSetId);
        changeSet.put("baseGraphVersion", graphVersion);
        changeSet.put("ops", normalized);
        changeSet.put(
                "pgSideEffects",
                request.pgSideEffects() != null ? request.pgSideEffects() : List.of());
        changeSet.put("invariants", List.of());
        changeSet.put(
                "stats",
                Map.of(
                        "nodeCreates",
                        countOp(normalized, "NODE_CREATE"),
                        "relCreates",
                        countOp(normalized, "REL_CREATE"),
                        "tagOps",
                        countOp(normalized, "TAG_ADD") + countOp(normalized, "TAG_REMOVE")));

        if ("CRITICAL".equals(risk)) {
            warnings.add("含 CONNECTS_VIA 或软删等高风险操作，合并需人工审批");
        }

        String changeSetJson;
        try {
            changeSetJson = objectMapper.writeValueAsString(changeSet);
            // validate round-trip
            objectMapper.readValue(changeSetJson, GraphChangeSet.class);
        } catch (Exception ex) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "PLAN_SERIALIZE_FAILED", "ChangeSet 序列化失败: " + ex.getMessage());
        }

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("opCount", normalized.size());
        preview.put("summary", request.summary());
        preview.put("changeSetId", changeSetId);

        return new GraphPlanResponse(
                graphVersion,
                partitionVersion,
                partitionKey,
                changeSetJson,
                risk,
                warnings,
                preview);
    }

    private static void ensureTempId(Map<String, Object> op, int index) {
        if (op.get("tempId") == null || String.valueOf(op.get("tempId")).isBlank()) {
            op.put("tempId", "tmp:node:" + index);
        }
    }

    private static int countOp(List<Map<String, Object>> ops, String kind) {
        int n = 0;
        for (Map<String, Object> op : ops) {
            if (kind.equalsIgnoreCase(str(op.get("op")))) {
                n++;
            }
        }
        return n;
    }

    private static String maxRisk(String a, String b) {
        return rank(a) >= rank(b) ? a : b;
    }

    private static int rank(String risk) {
        return switch (risk) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private static String titleCase(String kind) {
        String k = kind.trim().toUpperCase(Locale.ROOT);
        return switch (k) {
            case "SERVER" -> "Server";
            case "CLUSTER" -> "Cluster";
            case "SERVICE" -> "Service";
            case "DATABASE" -> "Database";
            case "NETWORK" -> "Network";
            case "TAG" -> "Tag";
            case "ENVIRONMENT" -> "Environment";
            default -> k.charAt(0) + k.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
