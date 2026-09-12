package com.archops.plan.domain;

import com.archops.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void frozenWireLiteralsParseToCatalogConstants() {
        assertThat(PlanStepAction.parse("SSH_PRECHECK").name()).isEqualTo("SSH_PRECHECK");
        assertThat(PlanStepAction.parse("MIGRATE_CONTAINER").name()).isEqualTo("MIGRATE_CONTAINER");
        assertThat(PlanStepAction.parse("REFRESH_OBSERVATION").name()).isEqualTo("REFRESH_OBSERVATION");
        assertThat(PlanStepAction.values()).containsExactly(
                PlanStepAction.SSH_PRECHECK,
                PlanStepAction.MIGRATE_CONTAINER,
                PlanStepAction.REFRESH_OBSERVATION);
    }
}
