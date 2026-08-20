package com.archops.curated.domain;

/**
 * Confirmation unit for a 草案 item. Only PENDING → ACCEPTED or PENDING → REJECTED.
 */
public enum CuratedDraftItemStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
