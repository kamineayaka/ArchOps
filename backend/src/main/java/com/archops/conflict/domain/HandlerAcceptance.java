package com.archops.conflict.domain;

/**
 * Handler acceptance on a conflict.
 * PENDING_ACCEPT is reserved for assign flow (ticket 11); ticket 05 uses NONE / ACCEPTED.
 */
public enum HandlerAcceptance {
    NONE,
    PENDING_ACCEPT,
    ACCEPTED
}
