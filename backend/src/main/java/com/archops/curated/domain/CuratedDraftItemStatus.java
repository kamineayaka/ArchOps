package com.archops.curated.domain;

/**
 * Confirmation unit for a 草案 item. Ticket 03 only creates PENDING;
 * ACCEPTED / REJECTED writes are ticket 04.
 */
public enum CuratedDraftItemStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
