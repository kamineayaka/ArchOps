package com.archops.conflict.domain;

/**
 * Conflict case lifecycle (tickets 04 / 09).
 * Equality alone never auto-closes — only PENDING_CLOSE + handler confirm → CLOSED.
 */
public enum ConflictStatus {
    OPEN,
    PENDING_CLOSE,
    CLOSED
}
