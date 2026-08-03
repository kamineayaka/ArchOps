package com.archops.graph.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;

/** Before-image journal for Neo4j GraphOp compensation after PG-side merge failure. */
public final class GraphApplyJournal {

    private final List<Entry> entries = new ArrayList<>();

    void recordNodeCreate(UUID elementId) {
        entries.add(Entry.nodeCreate(elementId));
    }

    void recordNodeUpdate(UUID elementId, Map<String, Object> previousProps, List<String> newlySetKeys) {
        entries.add(Entry.nodeUpdate(elementId, previousProps, newlySetKeys));
    }

    void recordNodeSoftDelete(UUID elementId, boolean previousEnabled, List<RelLifecycle> touchedRels) {
        entries.add(Entry.nodeSoftDelete(elementId, previousEnabled, touchedRels));
    }

    void recordRelCreate(String relElementId) {
        entries.add(Entry.relCreate(relElementId));
    }

    void recordRelUpdate(String relElementId, Map<String, Object> previousProps, List<String> newlySetKeys) {
        entries.add(Entry.relUpdate(relElementId, previousProps, newlySetKeys));
    }

    void recordRelDelete(
            String relElementId,
            String type,
            UUID fromId,
            UUID toId,
            Map<String, Object> previousProps) {
        entries.add(Entry.relDelete(relElementId, type, fromId, toId, previousProps));
    }

    List<Entry> entries() {
        return List.copyOf(entries);
    }

    void reverse(TransactionContext tx) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            entries.get(i).reverse(tx);
        }
    }

    record RelLifecycle(String elementId, boolean deleted, Object deletedAt) {}

    private record Entry(
            Kind kind,
            UUID nodeElementId,
            String relElementId,
            String relType,
            UUID fromId,
            UUID toId,
            Map<String, Object> previousProps,
            List<String> newlySetKeys,
            Boolean previousEnabled,
            List<RelLifecycle> touchedRels) {

        enum Kind {
            NODE_CREATE,
            NODE_UPDATE,
            NODE_SOFT_DELETE,
            REL_CREATE,
            REL_UPDATE,
            REL_DELETE
        }

        static Entry nodeCreate(UUID elementId) {
            return new Entry(Kind.NODE_CREATE, elementId, null, null, null, null, null, null, null, null);
        }

        static Entry nodeUpdate(UUID elementId, Map<String, Object> previous, List<String> newlySet) {
            return new Entry(Kind.NODE_UPDATE, elementId, null, null, null, null, previous, newlySet, null, null);
        }

        static Entry nodeSoftDelete(UUID elementId, boolean enabled, List<RelLifecycle> rels) {
            return new Entry(Kind.NODE_SOFT_DELETE, elementId, null, null, null, null, null, null, enabled, rels);
        }

        static Entry relCreate(String relElementId) {
            return new Entry(Kind.REL_CREATE, null, relElementId, null, null, null, null, null, null, null);
        }

        static Entry relUpdate(String relElementId, Map<String, Object> previous, List<String> newlySet) {
            return new Entry(Kind.REL_UPDATE, null, relElementId, null, null, null, previous, newlySet, null, null);
        }

        static Entry relDelete(
                String relElementId, String type, UUID fromId, UUID toId, Map<String, Object> previous) {
            return new Entry(Kind.REL_DELETE, null, relElementId, type, fromId, toId, previous, null, null, null);
        }

        void reverse(TransactionContext tx) {
            switch (kind) {
                case NODE_CREATE -> tx.run(
                                "MATCH (n:Asset {elementId: $elementId}) DETACH DELETE n",
                                Values.parameters("elementId", nodeElementId.toString()))
                        .consume();
                case NODE_UPDATE -> {
                    if (previousProps != null && !previousProps.isEmpty()) {
                        tx.run(
                                        """
                                        MATCH (n:Asset {elementId: $elementId})
                                        SET n += $props
                                        """,
                                        Values.parameters(
                                                "elementId", nodeElementId.toString(),
                                                "props", previousProps))
                                .consume();
                    }
                    if (newlySetKeys != null) {
                        for (String key : newlySetKeys) {
                            if (previousProps == null || !previousProps.containsKey(key)) {
                                tx.run(
                                                "MATCH (n:Asset {elementId: $elementId}) REMOVE n." + key,
                                                Values.parameters("elementId", nodeElementId.toString()))
                                        .consume();
                            }
                        }
                    }
                }
                case NODE_SOFT_DELETE -> {
                    tx.run(
                                    """
                                    MATCH (n:Asset {elementId: $elementId})
                                    SET n.deleted = false, n.deletedAt = null, n.enabled = $enabled
                                    """,
                                    Values.parameters(
                                            "elementId", nodeElementId.toString(),
                                            "enabled", previousEnabled == null || previousEnabled))
                            .consume();
                    if (touchedRels != null) {
                        for (RelLifecycle rel : touchedRels) {
                            tx.run(
                                            """
                                            MATCH ()-[r {elementId: $elementId}]->()
                                            SET r.deleted = $deleted, r.deletedAt = $deletedAt
                                            """,
                                            Values.parameters(
                                                    "elementId", rel.elementId(),
                                                    "deleted", rel.deleted(),
                                                    "deletedAt", rel.deletedAt()))
                                    .consume();
                        }
                    }
                }
                case REL_CREATE -> {
                    if (relElementId != null) {
                        tx.run(
                                        "MATCH ()-[r {elementId: $elementId}]->() DELETE r",
                                        Values.parameters("elementId", relElementId))
                                .consume();
                    }
                }
                case REL_UPDATE -> {
                    if (previousProps != null && !previousProps.isEmpty()) {
                        tx.run(
                                        """
                                        MATCH ()-[r {elementId: $elementId}]->()
                                        SET r += $props
                                        """,
                                        Values.parameters("elementId", relElementId, "props", previousProps))
                                .consume();
                    }
                    if (newlySetKeys != null) {
                        for (String key : newlySetKeys) {
                            if (previousProps == null || !previousProps.containsKey(key)) {
                                tx.run(
                                                "MATCH ()-[r {elementId: $elementId}]->() REMOVE r." + key,
                                                Values.parameters("elementId", relElementId))
                                        .consume();
                            }
                        }
                    }
                }
                case REL_DELETE -> {
                    if (relType == null || fromId == null || toId == null) {
                        return;
                    }
                    Map<String, Object> props = previousProps != null ? previousProps : Map.of();
                    tx.run(
                                    """
                                    MATCH (a:Asset {elementId: $fromId}), (b:Asset {elementId: $toId})
                                    CREATE (a)-[r:%s]->(b)
                                    SET r += $props
                                    """.formatted(relType),
                                    Values.parameters(
                                            "fromId", fromId.toString(),
                                            "toId", toId.toString(),
                                            "props", props))
                            .consume();
                }
            }
        }
    }
}
