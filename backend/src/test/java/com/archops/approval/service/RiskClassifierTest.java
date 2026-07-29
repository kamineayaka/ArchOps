package com.archops.approval.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.archops.approval.domain.RiskLevel;
import com.archops.asset.dbquery.SqlAccessClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiskClassifierTest {

    private RiskClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new RiskClassifier(new SqlAccessClassifier(), new ObjectMapper());
    }

    @Test
    void classifiesDestructiveShellAsHigh() {
        assertEquals(RiskLevel.HIGH, classifier.classify("ssh_exec", "{\"command\":\"rm -rf /tmp/x\"}"));
        assertEquals(RiskLevel.HIGH, classifier.classify("ssh_exec", "{\"command\":\"sudo reboot\"}"));
    }

    @Test
    void classifiesOperationalChangesAsMedium() {
        assertEquals(RiskLevel.MEDIUM, classifier.classify("ssh_exec", "{\"command\":\"systemctl restart nginx\"}"));
        assertEquals(RiskLevel.MEDIUM, classifier.classify("ssh_exec", "{\"command\":\"chmod 755 /opt/app\"}"));
    }

    @Test
    void classifiesBenignCommandsAsLow() {
        assertEquals(RiskLevel.LOW, classifier.classify("ssh_exec", "{\"command\":\"uptime\"}"));
        assertEquals(RiskLevel.LOW, classifier.classify("list_assets", "{}"));
    }

    @Test
    void classifiesDbQueryBySqlKind() {
        assertEquals(RiskLevel.LOW, classifier.classify("db_query", "{\"sql\":\"SELECT 1\"}"));
        assertEquals(RiskLevel.HIGH, classifier.classify("db_query", "{\"sql\":\"DELETE FROM t\"}"));
        assertEquals(RiskLevel.HIGH, classifier.classify("db_query", "{}"));
    }

    @Test
    void classifiesGraphChangeByOps() {
        assertEquals(
                RiskLevel.HIGH,
                classifier.classify("propose_graph_change", "{\"ops\":[{\"op\":\"NODE_CREATE\"}]}"));
        assertEquals(
                RiskLevel.MEDIUM,
                classifier.classify("propose_graph_change", "{\"ops\":[{\"op\":\"EDGE_UPDATE\"}]}"));
    }
}
