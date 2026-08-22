package com.archops.curated.domain;

/**
 * Where a 草案 came from. CHANGE_CURATED hangs on a conflict;
 * UNBOUND_CANDIDATE does not.
 */
public enum CuratedDraftOrigin {
    CHANGE_CURATED,
    UNBOUND_CANDIDATE
}
