package com.archops.curated.domain;

/**
 * Audit events owned by a 草案 (not hung on a conflict).
 */
public enum CuratedDraftEventType {
    DRAFT_CREATED,
    DRAFT_ITEM_ACCEPTED,
    DRAFT_ITEM_REJECTED
}
