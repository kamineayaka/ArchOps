package com.archops.graph.domain;

/** Closed set of first-wave graph relationship types. */
public enum GraphRelType {
    MEMBER_OF,
    RUNS_ON,
    DEPENDS_ON,
    CONNECTS_VIA,
    HAS_TAG;

    public static GraphRelType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("rel type required");
        }
        return GraphRelType.valueOf(raw.trim().toUpperCase());
    }
}
