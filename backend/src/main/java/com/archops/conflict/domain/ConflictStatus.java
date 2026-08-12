package com.archops.conflict.domain;

/**
 * Conflict case lifecycle (tickets 04 / 09 / 10).
 * Equality alone never auto-closes — only PENDING_CLOSE + handler confirm → CLOSED.
 * Heartbeat timeout / 观测空洞 → SUSPENDED (not CLOSED).
 */
public enum ConflictStatus {
    OPEN,
    PENDING_CLOSE,
    CLOSED,
    SUSPENDED
}
