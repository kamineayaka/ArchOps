package com.archops.curated;

/**
 * Immutable Docker object label key used for curated↔observed identity matching.
 * Convention: {@code archops.object_id=<containerObjectId>}.
 */
public final class CuratedObjectLabels {

    public static final String OBJECT_ID_KEY = "archops.object_id";

    private CuratedObjectLabels() {
    }

    public static String formatObjectIdLabel(String immutableObjectId) {
        return OBJECT_ID_KEY + "=" + immutableObjectId;
    }
}
