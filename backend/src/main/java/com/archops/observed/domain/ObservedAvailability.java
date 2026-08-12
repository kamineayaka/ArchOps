package com.archops.observed.domain;

/**
 * Availability of an observed fact value.
 * ABSENT = 观测消失 (usable value "does not exist"); never treat as 空洞.
 * Hollow is absence of a fresh available fact (ticket 10), not a stored availability.
 */
public enum ObservedAvailability {
    PRESENT,
    ABSENT
}
