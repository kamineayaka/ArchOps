package com.archops.curated.domain;

/**
 * Curated relation types. Slice pin: {@code RUNS_ON} = 运行于.
 */
public enum CuratedRelationType {
    RUNS_ON,
    /** Structured probe result compared with the task's curated value. */
    PROBE;

    public String labelZh() {
        return switch (this) {
            case RUNS_ON -> "运行于";
            case PROBE -> "探测";
        };
    }
}
