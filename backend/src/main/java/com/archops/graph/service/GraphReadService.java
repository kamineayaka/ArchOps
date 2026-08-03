package com.archops.graph.service;

import com.archops.common.exception.BusinessException;
import com.archops.graph.config.GraphProperties;
import com.archops.graph.dto.GraphQueryResponse;
import com.archops.graph.dto.GraphSnapshotResponse;
import com.archops.graph.dto.GraphSnapshotResponse.GraphEdgeDto;
import com.archops.graph.dto.GraphSnapshotResponse.GraphNodeDto;
import com.archops.knowledge.acl.AssetAclService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GraphReadService {

    private static final Pattern WRITE_KW = Pattern.compile(
            "\\b(CREATE|MERGE|DELETE|DETACH|SET|REMOVE|DROP|LOAD\\s+CSV|FOREACH|CALL|CREATE\\s+CONSTRAINT|CREATE\\s+INDEX)\\b",
            Pattern.CASE_INSENSITIVE);

    private final GraphProperties properties;
    private final Driver neo4jDriver;
    private final GraphVersionService graphVersionService;
    private final AssetAclService assetAclService;

    public GraphReadService(
            GraphProperties properties,
            Driver neo4jDriver,
            GraphVersionService graphVersionService,
            AssetAclService assetAclService) {
        this.properties = properties;
        this.neo4jDriver = neo4jDriver;
        this.graphVersionService = graphVersionService;
        this.assetAclService = assetAclService;
    }

    public GraphSnapshotResponse snapshot(Long userId, Collection<String> roles) {
        long version = graphVersionService.currentVersion();
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
            List<GraphNodeDto> nodes = new ArrayList<>();
            Result nodeResult = session.run(
                    """
                    MATCH (n:Asset)
                    WHERE coalesce(n.deleted, false) = false
                    RETURN n
                    """);
            while (nodeResult.hasNext()) {
                GraphNodeDto node = toNodeDto(nodeResult.next().get("n").asNode());
                if (node.pgAssetId() == null
                        || assetAclService.canAccessAsset(userId, roles, node.pgAssetId())) {
                    nodes.add(node);
                }
            }
            Set<String> includedElementIds = nodes.stream()
                    .map(GraphNodeDto::elementId)
                    .filter(id -> id != null)
                    .collect(java.util.stream.Collectors.toSet());

            List<GraphEdgeDto> edges = new ArrayList<>();
            Result edgeResult = session.run(
                    """
                    MATCH (a:Asset)-[r]->(b:Asset)
                    WHERE coalesce(a.deleted, false) = false
                      AND coalesce(b.deleted, false) = false
                      AND coalesce(r.deleted, false) = false
                    RETURN a.elementId AS fromId, b.elementId AS toId, type(r) AS type, r AS rel
                    """);
            while (edgeResult.hasNext()) {
                Record rec = edgeResult.next();
                Relationship rel = rec.get("rel").asRelationship();
                String fromId = rec.get("fromId").asString();
                String toId = rec.get("toId").asString();
                if (includedElementIds.contains(fromId) && includedElementIds.contains(toId)) {
                    edges.add(new GraphEdgeDto(
                            asString(rel.get("elementId")),
                            rec.get("type").asString(),
                            fromId,
                            toId,
                            toMap(rel.asMap())));
                }
            }
            return new GraphSnapshotResponse(version, nodes, edges);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE, "NEO4J_UNAVAILABLE", "Neo4j 不可用: " + ex.getMessage());
        }
    }

    public GraphQueryResponse query(String cypher, Long userId) {
        String trimmed = cypher != null ? cypher.trim() : "";
        if (trimmed.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CYPHER_REQUIRED", "Cypher 不能为空");
        }
        if (WRITE_KW.matcher(trimmed).find()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "CYPHER_WRITE_FORBIDDEN",
                    "只读查询禁止写语句；请使用计划模式提交 ChangeSet");
        }
        Instant start = Instant.now();
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
            Result result = session.run(trimmed);
            List<String> columns = result.keys();
            List<Map<String, Object>> rows = new ArrayList<>();
            Set<String> matched = new LinkedHashSet<>();
            while (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> row = new LinkedHashMap<>();
                for (String key : columns) {
                    Value value = record.get(key);
                    Object converted = convertValue(value, matched);
                    row.put(key, converted);
                }
                rows.add(row);
            }
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            return new GraphQueryResponse(columns, rows, List.copyOf(matched), elapsed);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "CYPHER_FAILED", "Cypher 执行失败: " + ex.getMessage());
        }
    }

    private Object convertValue(Value value, Set<String> matchedElementIds) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.type().name().equals("NODE")) {
            Node node = value.asNode();
            String elementId = asString(node.get("elementId"));
            if (elementId != null) {
                matchedElementIds.add(elementId);
            }
            return toNodeDto(node);
        }
        if (value.type().name().equals("RELATIONSHIP")) {
            Relationship rel = value.asRelationship();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("elementId", asString(rel.get("elementId")));
            map.put("type", rel.type());
            map.put("properties", toMap(rel.asMap()));
            return map;
        }
        if (value.type().name().equals("LIST")) {
            List<Object> list = new ArrayList<>();
            for (Value item : value.values()) {
                list.add(convertValue(item, matchedElementIds));
            }
            return list;
        }
        if (value.type().name().equals("MAP")) {
            return toMap(value.asMap());
        }
        Object plain = value.asObject();
        if (plain instanceof String s && looksLikeUuid(s)) {
            matchedElementIds.add(s);
        }
        return plain;
    }

    private GraphNodeDto toNodeDto(Node node) {
        List<String> labels = new ArrayList<>();
        node.labels().forEach(labels::add);
        Map<String, Object> props = toMap(node.asMap());
        return new GraphNodeDto(
                asString(node.get("elementId")),
                node.containsKey("pgAssetId") && !node.get("pgAssetId").isNull()
                        ? node.get("pgAssetId").asLong()
                        : null,
                asString(node.get("kind")),
                asString(node.get("name")),
                asString(node.get("host")),
                node.containsKey("port") && !node.get("port").isNull() ? node.get("port").asInt() : null,
                !node.containsKey("enabled") || node.get("enabled").asBoolean(true),
                node.containsKey("hasCredential") && node.get("hasCredential").asBoolean(false),
                asString(node.get("slug")),
                labels,
                props);
    }

    private static Map<String, Object> toMap(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Object v = e.getValue();
            if (v instanceof java.time.LocalDateTime ldt) {
                out.put(e.getKey(), ldt.toString());
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    private static String asString(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asString();
    }

    private static boolean looksLikeUuid(String s) {
        return s != null
                && s.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }
}
