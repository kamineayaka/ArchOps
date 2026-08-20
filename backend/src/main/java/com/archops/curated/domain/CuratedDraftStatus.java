package com.archops.curated.domain;

/**
 * 草案 lifecycle. Ticket 03 creates OPEN drafts; VOIDED is ticket 05.
 */
public enum CuratedDraftStatus {
    OPEN,
    VOIDED
}
