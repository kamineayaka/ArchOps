package com.archops.knowledge.hybrid;

import com.archops.graph.config.GraphProperties;
import com.archops.graph.service.GraphConnectPathService;
import com.archops.knowledge.architecture.PartitionKeys;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Read-only Neo4j neighborhood / path retrieval for hybrid RAG.
 * Soft-fails when graph is disabled or Neo4j is unreachable.
 */
@Service
public class GraphContextRetriever {

    private static final Logger log = LoggerFactory.getLogger(GraphContextRetriever.class);

    public static final int DEFAULT_HOPS = 2;
    public static final int DEFAULT_MAX_NODES = 30;

    private final GraphProperties properties;
    private final ObjectProvider<Driver> neo4jDriver;
    private final GraphConnectPathService connectPathService;

    public GraphContextRetriever(
            GraphProperties properties,
            ObjectProvider<Driver> neo4jDriver,
            GraphConnectPathService connectPathService) {
        this.properties = properties;
        this.neo4jDriver = neo4jDriver;
        this.connectPathService = connectPathService;
    }

    public NeighborhoodResult neighborhood(List<Long> pgAssetIds) {
        return neighborhood(pgAssetIds, DEFAULT_HOPS, DEFAULT_MAX_NODES);
    }

    public NeighborhoodResult neighborhood(List<Long> pgAssetIds, int hops, int maxNodes) {
        if (pgAssetIds == null || pgAssetIds.isEmpty()) {
            return NeighborhoodResult.unavailable("(no seed assets for graph neighborhood)");
        }
        if (!properties.isEnabled()) {
            return NeighborhoodResult.unavailable("(graph storage disabled)");
        }
        Driver driver = neo4jDriver.getIfAvailable();
        if (driver == null) {
            return NeighborhoodResult.unavailable("(Neo4j driver unavailable)");
        }

        int safeHops = Math.max(1, Math.min(hops, 2));
        int safeMax = Math.max(5, Math.min(maxNodes, 50));
        List<Long> seeds = pgAssetIds.stream().filter(id -> id != null && id > 0).distinct().toList();
        if (seeds.isEmpty()) {
            return NeighborhoodResult.unavailable("(no seed assets for graph neighborhood)");
        }

        Set<Long> relatedPgIds = new LinkedHashSet<>(seeds);
        Set<String> elementIds = new LinkedHashSet<>();
        Set<String> partitionKeys = new LinkedHashSet<>();
        partitionKeys.add(PartitionKeys.GLOBAL);
        List<String> lines = new ArrayList<>();

        try (Session session = driver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
            Result seedResult = session.run(
                    """
                    MATCH (seed:Asset)
                    WHERE seed.pgAssetId IN $ids
                      AND coalesce(seed.deleted, false) = false
                    RETURN seed.pgAssetId AS pgId,
                           seed.elementId AS elementId,
                           seed.name AS name,
                           seed.kind AS kind,
                           seed.host AS host,
                           seed.slug AS slug
                    """,
                    Values.parameters("ids", seeds));
            while (seedResult.hasNext()) {
                Record rec = seedResult.next();
                appendNodeLine(lines, "seed", rec);
                collectIds(relatedPgIds, elementIds, partitionKeys, rec);
            }

            Result edgeResult = session.run(
                    """
                    MATCH (seed:Asset)
                    WHERE seed.pgAssetId IN $ids
                      AND coalesce(seed.deleted, false) = false
                    MATCH (seed)-[r:MEMBER_OF|RUNS_ON|DEPENDS_ON|CONNECTS_VIA|HAS_TAG]-(n1:Asset)
                    WHERE coalesce(n1.deleted, false) = false
                      AND coalesce(r.deleted, false) = false
                    OPTIONAL MATCH (n1)-[r2:MEMBER_OF|RUNS_ON|DEPENDS_ON|CONNECTS_VIA|HAS_TAG]-(n2:Asset)
                    WHERE $hops >= 2
                      AND coalesce(n2.deleted, false) = false
                      AND coalesce(r2.deleted, false) = false
                      AND n2.pgAssetId <> seed.pgAssetId
                    RETURN seed.pgAssetId AS seedPgId,
                           seed.name AS seedName,
                           type(r) AS rel1,
                           startNode(r).pgAssetId AS r1From,
                           n1.pgAssetId AS n1PgId,
                           n1.elementId AS n1ElementId,
                           n1.name AS n1Name,
                           n1.kind AS n1Kind,
                           n1.host AS n1Host,
                           n1.slug AS n1Slug,
                           type(r2) AS rel2,
                           n2.pgAssetId AS n2PgId,
                           n2.elementId AS n2ElementId,
                           n2.name AS n2Name,
                           n2.kind AS n2Kind,
                           n2.host AS n2Host,
                           n2.slug AS n2Slug
                    LIMIT $limit
                    """,
                    Values.parameters("ids", seeds, "hops", safeHops, "limit", safeMax * 3));

            int edgeLines = 0;
            while (edgeResult.hasNext() && relatedPgIds.size() < safeMax) {
                Record rec = edgeResult.next();
                String seedName = asText(rec, "seedName");
                String rel1 = asText(rec, "rel1");
                String n1Label = nodeLabel(
                        asText(rec, "n1Name"),
                        asText(rec, "n1Kind"),
                        asLong(rec, "n1PgId"),
                        asText(rec, "n1Host"));
                if (rel1 != null && n1Label != null) {
                    Long r1From = asLong(rec, "r1From");
                    Long seedPg = asLong(rec, "seedPgId");
                    boolean outbound = r1From != null && r1From.equals(seedPg);
                    lines.add(String.format(
                            "- %s -[:%s]%s %s",
                            seedName != null ? seedName : "asset:" + seedPg,
                            rel1,
                            outbound ? "->" : "-",
                            n1Label));
                    edgeLines++;
                    collectNeighbor(
                            relatedPgIds,
                            elementIds,
                            partitionKeys,
                            asLong(rec, "n1PgId"),
                            asText(rec, "n1ElementId"),
                            asText(rec, "n1Kind"),
                            asText(rec, "n1Slug"));
                }
                String rel2 = asText(rec, "rel2");
                String n2Label = nodeLabel(
                        asText(rec, "n2Name"),
                        asText(rec, "n2Kind"),
                        asLong(rec, "n2PgId"),
                        asText(rec, "n2Host"));
                if (rel2 != null && n2Label != null && relatedPgIds.size() < safeMax) {
                    lines.add(String.format(
                            "- %s -[:%s]- %s",
                            asText(rec, "n1Name") != null ? asText(rec, "n1Name") : n1Label,
                            rel2,
                            n2Label));
                    edgeLines++;
                    collectNeighbor(
                            relatedPgIds,
                            elementIds,
                            partitionKeys,
                            asLong(rec, "n2PgId"),
                            asText(rec, "n2ElementId"),
                            asText(rec, "n2Kind"),
                            asText(rec, "n2Slug"));
                }
                if (edgeLines >= safeMax) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("Graph neighborhood retrieval failed: {}", ex.getMessage());
            return NeighborhoodResult.unavailable("(graph neighborhood failed: " + ex.getMessage() + ")");
        }

        if (lines.isEmpty()) {
            return new NeighborhoodResult(
                    true,
                    "(no graph edges for seed assets; seeds only)",
                    relatedPgIds.size(),
                    0,
                    List.copyOf(relatedPgIds),
                    List.copyOf(elementIds),
                    List.copyOf(partitionKeys));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Seed neighborhood (≤").append(safeHops).append(" hop, capped):\n");
        int shown = 0;
        for (String line : lines) {
            if (shown >= safeMax) {
                sb.append("- …truncated\n");
                break;
            }
            sb.append(line).append('\n');
            shown++;
        }
        return new NeighborhoodResult(
                true,
                sb.toString().trim(),
                relatedPgIds.size(),
                Math.min(lines.size(), safeMax),
                List.copyOf(relatedPgIds),
                List.copyOf(elementIds),
                List.copyOf(partitionKeys));
    }

    /**
     * Prefer topology shortestPath; also append CONNECTS_VIA jump chain for {@code toPgAssetId}.
     */
    public String describePath(Long fromPgAssetId, Long toPgAssetId) {
        if (fromPgAssetId == null || toPgAssetId == null) {
            return "(fromAssetId and toAssetId required)";
        }
        if (!properties.isEnabled()) {
            return "(graph storage disabled)";
        }
        Driver driver = neo4jDriver.getIfAvailable();
        if (driver == null) {
            return "(Neo4j driver unavailable)";
        }

        StringBuilder sb = new StringBuilder();
        try (Session session = driver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
            Result result = session.run(
                    """
                    MATCH (a:Asset {pgAssetId: $fromId}), (b:Asset {pgAssetId: $toId})
                    WHERE coalesce(a.deleted, false) = false
                      AND coalesce(b.deleted, false) = false
                    MATCH p = shortestPath(
                      (a)-[:MEMBER_OF|RUNS_ON|DEPENDS_ON|CONNECTS_VIA|HAS_TAG*1..6]-(b))
                    WHERE all(n IN nodes(p) WHERE coalesce(n.deleted, false) = false)
                    RETURN [n IN nodes(p) | coalesce(n.name, toString(n.pgAssetId))] AS names,
                           [r IN relationships(p) | type(r)] AS rels
                    LIMIT 1
                    """,
                    Values.parameters("fromId", fromPgAssetId, "toId", toPgAssetId));
            if (result.hasNext()) {
                Record rec = result.next();
                List<Object> names = rec.get("names").asList();
                List<Object> rels = rec.get("rels").asList();
                sb.append("Shortest topology path:\n");
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0) {
                        String rel = i - 1 < rels.size() ? String.valueOf(rels.get(i - 1)) : "?";
                        sb.append(" -[:").append(rel).append("]-> ");
                    }
                    sb.append(names.get(i));
                }
                sb.append('\n');
            } else {
                sb.append("(no topology path within 6 hops)\n");
            }
        } catch (Exception ex) {
            log.warn("Graph path retrieval failed: {}", ex.getMessage());
            sb.append("(path query failed: ").append(ex.getMessage()).append(")\n");
        }

        List<Long> jumps = connectPathService.resolveJumpAssetIds(toPgAssetId);
        sb.append("CONNECTS_VIA jump chain for toAssetId=").append(toPgAssetId).append(": ");
        if (jumps.isEmpty()) {
            sb.append("(direct / none)");
        } else {
            sb.append(jumps);
        }
        return sb.toString().trim();
    }

    private static void appendNodeLine(List<String> lines, String role, Record rec) {
        lines.add(String.format(
                "- [%s] %s",
                role,
                nodeLabel(
                        asText(rec, "name"),
                        asText(rec, "kind"),
                        asLong(rec, "pgId"),
                        asText(rec, "host"))));
    }

    private static void collectIds(
            Set<Long> relatedPgIds,
            Set<String> elementIds,
            Set<String> partitionKeys,
            Record rec) {
        Long pgId = asLong(rec, "pgId");
        String elementId = asText(rec, "elementId");
        String kind = asText(rec, "kind");
        String slug = asText(rec, "slug");
        collectNeighbor(relatedPgIds, elementIds, partitionKeys, pgId, elementId, kind, slug);
    }

    private static void collectNeighbor(
            Set<Long> relatedPgIds,
            Set<String> elementIds,
            Set<String> partitionKeys,
            Long pgId,
            String elementId,
            String kind,
            String slug) {
        if (pgId != null) {
            relatedPgIds.add(pgId);
            partitionKeys.add(PartitionKeys.asset(pgId));
        }
        if (elementId != null && !elementId.isBlank()) {
            elementIds.add(elementId);
            partitionKeys.add("asset:" + elementId);
            if ("CLUSTER".equalsIgnoreCase(kind)) {
                partitionKeys.add("cluster:" + elementId);
            }
        }
        if ("TAG".equalsIgnoreCase(kind) && slug != null && !slug.isBlank()) {
            try {
                partitionKeys.add(PartitionKeys.tag(slug));
            } catch (Exception ignored) {
                // invalid slug — skip
            }
        }
    }

    private static String nodeLabel(String name, String kind, Long pgId, String host) {
        if (name == null && pgId == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(name != null ? name : "asset");
        sb.append('{');
        if (kind != null) {
            sb.append("kind=").append(kind);
        }
        if (pgId != null) {
            if (kind != null) {
                sb.append(' ');
            }
            sb.append("id=").append(pgId);
        }
        if (host != null && !host.isBlank()) {
            sb.append(" host=").append(host);
        }
        sb.append('}');
        return sb.toString();
    }

    private static String asText(Record rec, String key) {
        if (rec == null || !rec.containsKey(key) || rec.get(key).isNull()) {
            return null;
        }
        return rec.get(key).asString();
    }

    private static Long asLong(Record rec, String key) {
        if (rec == null || !rec.containsKey(key) || rec.get(key).isNull()) {
            return null;
        }
        return rec.get(key).asLong();
    }

    public record NeighborhoodResult(
            boolean available,
            String promptText,
            int nodeCount,
            int edgeCount,
            List<Long> relatedPgAssetIds,
            List<String> relatedElementIds,
            List<String> suggestedPartitionKeys) {

        static NeighborhoodResult unavailable(String message) {
            return new NeighborhoodResult(false, message, 0, 0, List.of(), List.of(), List.of());
        }
    }
}
