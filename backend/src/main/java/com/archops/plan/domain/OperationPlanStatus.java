package com.archops.plan.domain;

/**
 * Operation plan lifecycle. Ticket 07 covers DRAFT_REVIEW → APPROVED (execution intent).
 * EXECUTING+ is ticket 08.
 */
public enum OperationPlanStatus {
    DRAFT_REVIEW,
    APPROVED,
    EXECUTING,
    COMPLETED,
    VOIDED,
    SUPERSEDED
}
