package com.archops.plan.domain;

import com.archops.common.exception.BusinessException;

/**
 * Frozen FIX_ACTUAL tool names. Wire / {@code stepsJson} / HTTP stay these literals.
 */
public enum PlanStepAction {
    ;

    public static PlanStepAction parse(String wire) {
        throw new BusinessException("PLAN_STEP_UNKNOWN",
                "Unknown frozen plan action: " + wire);
    }
}
