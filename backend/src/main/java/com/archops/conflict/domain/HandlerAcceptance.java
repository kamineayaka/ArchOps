package com.archops.conflict.domain;

/**
 * Handler acceptance on a conflict.
 * NONE = no handler; PENDING_ACCEPT = assigned/transferred awaiting consent; ACCEPTED = may open plans.
 */
public enum HandlerAcceptance {
    NONE,
    PENDING_ACCEPT,
    ACCEPTED
}
