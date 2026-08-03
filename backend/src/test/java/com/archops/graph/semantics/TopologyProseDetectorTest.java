package com.archops.graph.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archops.asset.domain.AssetKind;
import com.archops.common.exception.BusinessException;
import com.archops.graph.domain.GraphRelType;
import org.junit.jupiter.api.Test;

class TopologyProseDetectorTest {

    @Test
    void detectsHardJumpProse() {
        var result = TopologyProseDetector.scan("SSH 跳板机是 bastion-1，请 via jump 连接");
        assertTrue(result.isHard());
        assertTrue(result.blocksAutoMerge());
    }

    @Test
    void detectsWarnDependsLanguage() {
        var result = TopologyProseDetector.scan("service-a depends on redis");
        assertEquals(TopologyProseDetector.Level.WARN, result.level());
        assertTrue(result.blocksAutoMerge());
    }

    @Test
    void ignoresBenignRemark() {
        var result = TopologyProseDetector.scan("生产库只读账号，变更窗口周五");
        assertEquals(TopologyProseDetector.Level.NONE, result.level());
    }

    @Test
    void topologyPredicates() {
        assertTrue(TopologyProseDetector.isTopologyEdgePredicate("depends_on"));
        assertTrue(TopologyProseDetector.isTopologyEdgePredicate("CONNECTS_VIA"));
        assertFalse(TopologyProseDetector.isTopologyEdgePredicate("role"));
    }
}

class GraphRelEndpointRulesTest {

    @Test
    void connectsViaRequiresServers() {
        GraphRelEndpointRules.validate(GraphRelType.CONNECTS_VIA, AssetKind.SERVER, AssetKind.SERVER);
        assertThrows(
                BusinessException.class,
                () -> GraphRelEndpointRules.validate(
                        GraphRelType.CONNECTS_VIA, AssetKind.SERVER, AssetKind.DATABASE));
    }

    @Test
    void runsOnServiceToServer() {
        GraphRelEndpointRules.validate(GraphRelType.RUNS_ON, AssetKind.SERVICE, AssetKind.SERVER);
        assertThrows(
                BusinessException.class,
                () -> GraphRelEndpointRules.validate(GraphRelType.RUNS_ON, AssetKind.SERVER, AssetKind.SERVER));
    }

    @Test
    void dependsOnRejectsTags() {
        assertThrows(
                BusinessException.class,
                () -> GraphRelEndpointRules.validate(GraphRelType.DEPENDS_ON, AssetKind.SERVER, AssetKind.TAG));
    }
}
