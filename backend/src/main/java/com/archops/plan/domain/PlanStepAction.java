package com.archops.plan.domain;

import com.archops.common.exception.BusinessException;

import java.util.Optional;

/**
 * Frozen FIX_ACTUAL tool names. Wire / {@code stepsJson} / HTTP stay these literals.
 */
public enum PlanStepAction {
    SSH_PRECHECK,
    MIGRATE_CONTAINER,
    REFRESH_OBSERVATION;

    public static PlanStepAction parse(String wire) {
        return tryParse(wire).orElseThrow(() -> unknown(wire));
    }

    /**
     * Absent when the wire is not one of the three frozen tools. Fake SSH uses this
     * so unknown cannot become a JSON product path; production parse stays fail-closed.
     */
    public static Optional<PlanStepAction> tryParse(String wire) {
        if (wire == null || wire.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(PlanStepAction.valueOf(wire));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static BusinessException unknown(String wire) {
        return new BusinessException("PLAN_STEP_UNKNOWN",
                "Unknown frozen plan action: " + wire);
    }
}
