package com.archops.knowledge.classifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChangeClassifierTest {

    private final ChangeClassifier classifier = new ChangeClassifier();

    @Test
    void inventoryAndGraphToolsDefaultToL0() {
        assertEquals(ChangeLevel.L0, classifier.classify("list_assets", "{}", "hosts").level());
        assertEquals(ChangeLevel.L0, classifier.classify("graph_neighborhood", "{}", "nodes").level());
        assertEquals(ChangeLevel.L0, classifier.classify("graph_path", "{}", "path").level());
    }

    @Test
    void proposalToolsDoNotEscalateGraphProposal() {
        assertEquals(ChangeLevel.L1, classifier.classify("propose_architecture_update", "{}", "ok").level());
        assertEquals(ChangeLevel.L0, classifier.classify("propose_graph_change", "{}", "ok").level());
    }

    @Test
    void readOnlyDbQueryIsL0() {
        assertEquals(
                ChangeLevel.L0,
                classifier.classify("db_query", "{\"sql\":\"SELECT 1\"}", "1").level());
    }

    @Test
    void discoveryKeywordsStillEscalateReadTools() {
        assertEquals(
                ChangeLevel.L1,
                classifier.classify("list_assets", "{}", "found namenode role").level());
    }
}
