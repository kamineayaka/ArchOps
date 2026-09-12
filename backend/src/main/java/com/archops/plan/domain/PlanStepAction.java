package com.archops.plan.domain;

import com.archops.common.exception.BusinessException;

/**
 * Frozen FIX_ACTUAL tool names. Wire / {@code stepsJson} / HTTP stay these literals.
 */
public enum PlanStepAction {
    SSH_PRECHECK,
    MIGRATE_CONTAINER,
    REFRESH_OBSERVATION;

    public static PlanStepAction parse(String wire) {
        if (wire == null || wire.isBlank()) {
            throw unknown(wire);
        }
        try {
            return PlanStepAction.valueOf(wire);
        } catch (IllegalArgumentException ex) {
            throw unknown(wire);
        }
    }

    private static BusinessException unknown(String wire) {
        return new BusinessException("PLAN_STEP_UNKNOWN",
                "Unknown frozen plan action: " + wire);
    }
}
