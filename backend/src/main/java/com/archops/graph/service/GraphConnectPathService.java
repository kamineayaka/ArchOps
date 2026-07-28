package com.archops.graph.service;

import com.archops.graph.config.GraphProperties;
import java.util.ArrayList;
import java.util.List;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves SSH jump hops from Neo4j {@code CONNECTS_VIA} (semantic B: target fans out ordered edges).
 * Returns empty when no edges exist or Neo4j is temporarily unreachable — callers fall back to legacy credential jumps.
 */
@Service
public class GraphConnectPathService {

    private static final Logger log = LoggerFactory.getLogger(GraphConnectPathService.class);

    private final GraphProperties properties;
    private final Driver neo4jDriver;

    public GraphConnectPathService(GraphProperties properties, Driver neo4jDriver) {
        this.properties = properties;
        this.neo4jDriver = neo4jDriver;
    }

    /**
     * Ordered jump asset IDs for dialing {@code targetPgAssetId}.
     * Empty list means direct dial (or caller should use legacy jumps).
     */
    public List<Long> resolveJumpAssetIds(Long targetPgAssetId) {
        if (targetPgAssetId == null) {
            return List.of();
        }
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
            Result result = session.run(
                    """
                    MATCH (t:Asset {pgAssetId: $pgAssetId})-[r:CONNECTS_VIA]->(j:Asset)
                    WHERE coalesce(t.deleted, false) = false
                      AND coalesce(r.deleted, false) = false
                      AND coalesce(j.deleted, false) = false
                    RETURN j.pgAssetId AS jumpId, coalesce(r.order, 0) AS ord
                    ORDER BY ord ASC
                    """,
                    Values.parameters("pgAssetId", targetPgAssetId));
            List<Long> jumps = new ArrayList<>();
            while (result.hasNext()) {
                Record rec = result.next();
                if (!rec.get("jumpId").isNull()) {
                    jumps.add(rec.get("jumpId").asLong());
                }
            }
            return List.copyOf(jumps);
        } catch (Exception ex) {
            log.warn("Failed to resolve CONNECTS_VIA for asset {}: {}", targetPgAssetId, ex.getMessage());
            return List.of();
        }
    }

    public void markHasCredential(Long pgAssetId, boolean hasCredential) {
        if (pgAssetId == null) {
            return;
        }
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(properties.getDatabase()))) {
            session.run(
                    """
                    MATCH (n:Asset {pgAssetId: $pgAssetId})
                    WHERE coalesce(n.deleted, false) = false
                    SET n.hasCredential = $hasCredential
                    """,
                    Values.parameters("pgAssetId", pgAssetId, "hasCredential", hasCredential));
        } catch (Exception ex) {
            log.warn("Failed to update hasCredential for asset {}: {}", pgAssetId, ex.getMessage());
        }
    }
}
