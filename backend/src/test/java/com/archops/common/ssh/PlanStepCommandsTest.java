package com.archops.common.ssh;

import com.archops.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
}
