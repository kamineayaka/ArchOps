package com.archops.common.ssh;

import com.archops.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanStepCommandsTest {

    @Test
    void unknownActionThrowsPlanStepUnknownInsteadOfFailOpenCommand() {
        assertThatThrownBy(() -> PlanStepCommands.command("NOT_A_FROZEN_ACTION", Map.of(), "host-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unknown frozen plan action: NOT_A_FROZEN_ACTION")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PLAN_STEP_UNKNOWN");
    }

    @Test
    void frozenActionsMapToExistingCommandLines() {
        assertThat(PlanStepCommands.command("SSH_PRECHECK", Map.of(), "host-obs"))
                .isEqualTo("archops-precheck --host host-obs");
        assertThat(PlanStepCommands.command(
                "MIGRATE_CONTAINER",
                Map.of("fromHostId", "host-from", "toHostId", "host-to", "subjectId", "ctr-1"),
                "host-from"))
                .isEqualTo("archops-migrate --from host-from --to host-to --subject ctr-1");
        assertThat(PlanStepCommands.command(
                "REFRESH_OBSERVATION",
                Map.of("subjectId", "ctr-1"),
                "host-curated"))
                .isEqualTo("archops-refresh-observation --subject ctr-1 --host host-curated");
    }
}
