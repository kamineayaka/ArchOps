package com.archops.conflict.domain;

/**
 * Minimal conflict lifecycle audit events (ticket 09).
 */
public enum ConflictEventType {
    WARNED,
    UPGRADED,
    ACKNOWLEDGED,
    HANDLER_ACCEPTED,
    PLAN_COMPLETED,
    PENDING_CLOSE,
    CONFIRM_FAILED,
    CLOSED
}
