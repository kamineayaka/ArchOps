package com.archops.conflict.domain;

/**
 * Minimal conflict lifecycle audit events (tickets 09 / 10).
 */
public enum ConflictEventType {
    WARNED,
    UPGRADED,
    ACKNOWLEDGED,
    HANDLER_ASSIGNED,
    HANDLER_ACCEPTED,
    HANDLER_REJECTED,
    HANDLER_TRANSFER_OFFERED,
    PLAN_COMPLETED,
    PENDING_CLOSE,
    CONFIRM_FAILED,
    CLOSED,
    SUSPENDED,
    PLAN_VOIDED
}
