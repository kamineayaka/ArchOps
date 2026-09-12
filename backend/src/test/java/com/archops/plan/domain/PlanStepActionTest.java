package com.archops.plan.domain;

import com.archops.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanStepActionTest {

    @Test
    void unknownWireThrowsPlanStepUnknown() {
        assertThatThrownBy(() -> PlanStepAction.parse("NOT_A_FROZEN_ACTION"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unknown frozen plan action: NOT_A_FROZEN_ACTION")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("PLAN_STEP_UNKNOWN");
    }
}
