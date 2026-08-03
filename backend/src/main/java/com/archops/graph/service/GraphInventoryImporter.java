package com.archops.graph.service;

import com.archops.asset.domain.Asset;
import com.archops.asset.domain.SshCredential;
import com.archops.asset.repository.AssetRepository;
import com.archops.asset.repository.SshCredentialRepository;
import com.archops.graph.config.GraphProperties;
import com.archops.graph.domain.GraphLabels;
import com.archops.graph.domain.GraphMeta;
import com.archops.knowledge.acl.AssetAclService;
import com.archops.user.domain.Role;
import com.archops.user.domain.User;
import com.archops.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent cold-start / upgrade importer:
 * <ul>
 *   <li>PG assets missing from Neo4j → create graph nodes</li>
 *   <li>Legacy {@code jump_asset_ids} → {@code CONNECTS_VIA} edges</li>
 *   <li>Legacy {@code parent_id} → {@code MEMBER_OF} edges</li>
 *   <li>Empty {@code user_assets} → grant all active assets to ADMIN users</li>
 * </ul>
 * Progress is recorded in {@code graph_migration_map}.
 */
@Component
@Order(50)
public class GraphInventoryImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GraphInventoryImporter.class);

    private final Driver neo4jDriver;
    private final GraphProperties graphProperties;
    private final AssetRepository assetRepository;
    private final SshCredentialRepository sshCredentialRepository;
    private final AssetAclService assetAclService;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final GraphVersionService graphVersionService;

    public GraphInventoryImporter(
            Driver neo4jDriver,
            GraphProperties graphProperties,
            AssetRepository assetRepository,
            SshCredentialRepository sshCredentialRepository,
            AssetAclService assetAclService,
            UserRepository userRepository,
            JdbcTemplate jdbcTemplate,
            GraphVersionService graphVersionService) {
        this.neo4jDriver = neo4jDriver;
        this.graphProperties = graphProperties;
        this.assetRepository = assetRepository;
        this.sshCredentialRepository = sshCredentialRepository;
        this.assetAclService = assetAclService;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.graphVersionService = graphVersionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread.ofVirtual().name("graph-inventory-import").start(() -> {
            try {
                // Wait briefly for Neo4j schema init
                Thread.sleep(3_000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                importAll();
            } catch (Exception ex) {
                log.error("Graph inventory import failed", ex);
            }
        });
    }

    @Transactional
    public void importAll() {
        List<Asset> assets = assetRepository.findByDeletedAtIsNull();
        int nodes = 0;
        int jumps = 0;
        int parents = 0;
        try (Session session = neo4jDriver.session(SessionConfig.forDatabase(graphProperties.getDatabase()))) {
            for (Asset asset : assets) {
                if (ensureNode(session, asset)) {
                    nodes++;
                    markMap("ASSET", String.valueOf(asset.getId()), "NODE", asset.getElementId(), null, null, null, "DONE");
                }
            }
            for (Asset asset : assets) {
                if (asset.getParentId() != null) {
                    Asset parent = assetRepository.findByIdAndDeletedAtIsNull(asset.getParentId()).orElse(null);
                    if (parent != null
                            && ensureRel(
                                    session,
                                    "MEMBER_OF",
                                    asset.getElementId(),
                                    parent.getElementId(),
                                    Map.of())) {
                        parents++;
                        markMap(
                                "PARENT",
                                asset.getId() + "->" + parent.getId(),
                                "REL",
                                null,
                                "MEMBER_OF",
                                asset.getElementId(),
                                parent.getElementId(),
                                "DONE");
                    }
                }
            }
            for (Asset asset : assets) {
                SshCredential cred = sshCredentialRepository
                        .findByAssetIdAndDeletedAtIsNull(asset.getId())
                        .orElse(null);
                if (cred == null || cred.getJumpAssetIds() == null || cred.getJumpAssetIds().isEmpty()) {
                    continue;
                }
                int order = 0;
                for (Long jumpId : cred.getJumpAssetIds()) {
                    Asset jump = assetRepository.findByIdAndDeletedAtIsNull(jumpId).orElse(null);
                    if (jump == null) {
                        continue;
                    }
                    Map<String, Object> props = new HashMap<>();
                    props.put("order", order);
                    props.put("protocol", "ssh");
                    props.put("elementId", UUID.randomUUID().toString());
                    if (ensureRel(session, "CONNECTS_VIA", asset.getElementId(), jump.getElementId(), props)) {
                        jumps++;
                        markMap(
                                "JUMP",
                                asset.getId() + "->" + jumpId + "#" + order,
                                "REL",
                                null,
                                "CONNECTS_VIA",
                                asset.getElementId(),
                                jump.getElementId(),
                                "DONE");
                    }
                    order++;
                }
                // Clear legacy jump list after successful edge creation for this credential.
                if (order > 0) {
                    cred.setJumpAssetIds(List.of());
                    sshCredentialRepository.save(cred);
                }
            }
        }

        int grants = bootstrapAdminAcl(assets);
        if (nodes > 0 || jumps > 0 || parents > 0) {
            try {
                GraphMeta meta = graphVersionService.lockGlobal();
                graphVersionService.bump(meta, null);
            } catch (Exception ex) {
                log.warn("Graph inventory changed but graph version bump failed", ex);
            }
        }
        log.info(
                "Graph inventory import complete: nodesCreatedOrEnsured={} parentEdges={} jumpEdges={} aclGrants={}",
                nodes,
                parents,
                jumps,
                grants);
    }

    private boolean ensureNode(Session session, Asset asset) {
        String elementId = asset.getElementId().toString();
        var existing = session.run(
                "MATCH (n:Asset {elementId: $elementId}) RETURN n.elementId AS id",
                Values.parameters("elementId", elementId));
        if (existing.hasNext()) {
            // Keep projection in sync for pgAssetId / hasCredential
            boolean hasCred = sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()).isPresent();
            session.run(
                    """
                    MATCH (n:Asset {elementId: $elementId})
                    SET n.pgAssetId = $pgAssetId,
                        n.name = $name,
                        n.kind = $kind,
                        n.host = $host,
                        n.port = $port,
                        n.enabled = $enabled,
                        n.hasCredential = $hasCredential,
                        n.deleted = false,
                        n.updatedAt = $updatedAt
                    """,
                    Values.parameters(
                            "elementId", elementId,
                            "pgAssetId", asset.getId(),
                            "name", asset.getName(),
                            "kind", asset.getKind().name(),
                            "host", asset.getHost(),
                            "port", asset.getPort(),
                            "enabled", asset.isEnabled(),
                            "hasCredential", hasCred,
                            "updatedAt", LocalDateTime.now(ZoneOffset.UTC)));
            return false;
        }
        List<String> labels = GraphLabels.forKind(asset.getKind());
        String labelSuffix = GraphLabels.cypherLabelSuffix(labels);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("elementId", elementId);
        props.put("pgAssetId", asset.getId());
        props.put("name", asset.getName());
        props.put("kind", asset.getKind().name());
        props.put("host", asset.getHost());
        props.put("port", asset.getPort());
        props.put("enabled", asset.isEnabled());
        props.put(
                "hasCredential",
                sshCredentialRepository.findByAssetIdAndDeletedAtIsNull(asset.getId()).isPresent());
        props.put("deleted", false);
        props.put("createdAt", LocalDateTime.now(ZoneOffset.UTC));
        props.put("updatedAt", LocalDateTime.now(ZoneOffset.UTC));
        session.run("CREATE (n" + labelSuffix + ") SET n += $props", Values.parameters("props", props)).consume();
        asset.setGraphSyncedAt(Instant.now());
        assetRepository.save(asset);
        return true;
    }

    private boolean ensureRel(
            Session session, String type, UUID fromId, UUID toId, Map<String, Object> props) {
        var existing = session.run(
                """
                MATCH (a:Asset {elementId: $fromId})-[r:%s]->(b:Asset {elementId: $toId})
                WHERE coalesce(r.deleted, false) = false
                RETURN r.elementId AS id
                """.formatted(type),
                Values.parameters("fromId", fromId.toString(), "toId", toId.toString()));
        if (existing.hasNext()) {
            return false;
        }
        Map<String, Object> relProps = new LinkedHashMap<>(props != null ? props : Map.of());
        if (!relProps.containsKey("elementId")) {
            relProps.put("elementId", UUID.randomUUID().toString());
        }
        relProps.put("deleted", false);
        relProps.put("createdAt", LocalDateTime.now(ZoneOffset.UTC));
        session.run(
                        """
                        MATCH (a:Asset {elementId: $fromId}), (b:Asset {elementId: $toId})
                        CREATE (a)-[r:%s]->(b)
                        SET r += $props
                        """.formatted(type),
                        Values.parameters(
                                "fromId", fromId.toString(),
                                "toId", toId.toString(),
                                "props", relProps))
                .consume();
        return true;
    }

    private int bootstrapAdminAcl(List<Asset> assets) {
        if (assetAclService.countAssignments() > 0 || assets.isEmpty()) {
            return 0;
        }
        int grants = 0;
        for (User user : userRepository.findAll()) {
            boolean admin = user.getRoles().stream().map(Role::getName).anyMatch("ADMIN"::equalsIgnoreCase);
            if (!admin) {
                continue;
            }
            for (Asset asset : assets) {
                assetAclService.grant(user.getId(), asset.getId());
                grants++;
            }
        }
        if (grants > 0) {
            log.info("Bootstrapped user_assets for ADMIN users ({} grants)", grants);
        }
        return grants;
    }

    private void markMap(
            String sourceType,
            String sourceKey,
            String targetKind,
            UUID elementId,
            String relType,
            UUID fromId,
            UUID toId,
            String status) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO graph_migration_map
                        (source_type, source_key, target_kind, element_id, rel_type, from_element_id, to_element_id, status, detail, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, '{}'::jsonb, NOW())
                    ON CONFLICT (source_type, source_key) DO UPDATE
                    SET status = EXCLUDED.status,
                        element_id = COALESCE(EXCLUDED.element_id, graph_migration_map.element_id),
                        rel_type = COALESCE(EXCLUDED.rel_type, graph_migration_map.rel_type),
                        from_element_id = COALESCE(EXCLUDED.from_element_id, graph_migration_map.from_element_id),
                        to_element_id = COALESCE(EXCLUDED.to_element_id, graph_migration_map.to_element_id),
                        updated_at = NOW()
                    """,
                    sourceType,
                    sourceKey,
                    targetKind,
                    elementId,
                    relType,
                    fromId,
                    toId,
                    status);
        } catch (Exception ex) {
            log.debug("graph_migration_map upsert skipped: {}", ex.getMessage());
        }
    }
}
