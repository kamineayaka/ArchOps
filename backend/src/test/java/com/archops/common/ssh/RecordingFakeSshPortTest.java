package com.archops.common.ssh;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingFakeSshPortTest {

    @Test
    void defaultStdoutForFrozenActionsIsJsonMatchingB3Expected() {
        RecordingFakeSshPort port = new RecordingFakeSshPort();

        assertThat(stdout(port, "SSH_PRECHECK"))
                .isEqualTo("{\"precheck\":\"passed\",\"source\":\"fake\"}");
        assertThat(stdout(port, "MIGRATE_CONTAINER"))
                .isEqualTo("{\"migrated\":\"true\",\"source\":\"fake\"}");
        assertThat(stdout(port, "REFRESH_OBSERVATION"))
                .isEqualTo("{\"refresh\":\"ok\",\"source\":\"fake\"}");
    }

    @Test
    void unknownActionStdoutIsNotAFrozenJsonProductPath() {
        RecordingFakeSshPort port = new RecordingFakeSshPort();

        String stdout = stdout(port, "NOT_A_FROZEN_ACTION");
        assertThat(stdout).isEqualTo("fake-ok NOT_A_FROZEN_ACTION");
        assertThat(stdout).doesNotContain("precheck", "migrated", "refresh");
    }

    private static String stdout(RecordingFakeSshPort port, String action) {
        SshExecResult result = port.exec(new SshExecRequest("host-1", "cmd", action, 1, Map.of()));
        assertThat(result.success()).isTrue();
        return result.stdout();
    }
}
