package com.archops.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.archops.approval.domain.RiskLevel;
import org.junit.jupiter.api.Test;

class RiskClassifierReadonlyToolsTest {

    private final RiskClassifier classifier = new RiskClassifier();

    @Test
    void readonlyProbeToolsAreAlwaysLow() {
        assertThat(classifier.classify("list_assets", "{\"kind\":\"DATABASE\"}")).isEqualTo(RiskLevel.LOW);
        assertThat(classifier.classify("db_ping", "{\"assetId\":1}")).isEqualTo(RiskLevel.LOW);
        assertThat(classifier.classify("k8s_get", "{\"assetId\":2,\"resource\":\"ns\"}")).isEqualTo(RiskLevel.LOW);
        // Argument text must not escalate readonly probes
        assertThat(classifier.classify("db_ping", "rm -rf /")).isEqualTo(RiskLevel.LOW);
        assertThat(classifier.classify("k8s_get", "kubectl delete ns foo")).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void sshExecStillClassifiesFromPayload() {
        assertThat(classifier.classify("ssh_exec", "{\"command\":\"rm -rf /tmp\"}")).isEqualTo(RiskLevel.HIGH);
        assertThat(classifier.classify("ssh_exec", "{\"command\":\"df -h\"}")).isEqualTo(RiskLevel.LOW);
    }
}
