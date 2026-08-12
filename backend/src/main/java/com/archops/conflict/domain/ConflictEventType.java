package com.archops.conflict.domain;

/**
 * Minimal conflict lifecycle audit events (tickets 09 / 10).
 */
public enum ConflictEventType {
    WARNED,
    UPGRADED,
    ACKNOWLEDGED,
    HANDLER_ACCEPTED,
    PLAN_COMPLETED,
    PENDING_CLOSE,
    CONFIRM_FAILED,
    CLOSED,
    SUSPENDED,
    PLAN_VOIDED
}
