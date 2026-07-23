package com.archops.knowledge.classifier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangeClassifierTest {

    private ChangeClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ChangeClassifier();
    }

    @Test
    void dfIsL0() {
        Classification c = classifier.classify(
                "ssh_exec",
                "{\"assetId\":1,\"command\":\"df -h\"}",
                "Filesystem Size Used Avail Use% Mounted on\n/dev/sda1 100G 40G 60G 40% /");
        assertThat(c.level()).isEqualTo(ChangeLevel.L0);
    }

    @Test
    void namenodeDiscoveryIsL1() {
        Classification c = classifier.classify(
                "ssh_exec",
                "{\"assetId\":2,\"command\":\"jps\"}",
                "1234 NameNode\n5678 DataNode");
        assertThat(c.level()).isEqualTo(ChangeLevel.L1);
        assertThat(c.rationale()).containsIgnoringCase("discovery");
    }

    @Test
    void kubectlApplyIsL2() {
        Classification c = classifier.classify(
                "ssh_exec",
                "{\"assetId\":3,\"command\":\"kubectl apply -f deploy.yaml\"}",
                "deployment.apps/foo configured");
        assertThat(c.level()).isEqualTo(ChangeLevel.L2);
    }

    @Test
    void proposeToolIsL1() {
        Classification c = classifier.classify(
                "propose_architecture_update",
                "{\"partitionKey\":\"asset:1\",\"summary\":\"nn\"}",
                "Created proposal");
        assertThat(c.level()).isEqualTo(ChangeLevel.L1);
    }
}
