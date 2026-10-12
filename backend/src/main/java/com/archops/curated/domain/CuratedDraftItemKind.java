package com.archops.curated.domain;

/**
 * Draft item kinds: change-curated 运行于 retarget, unbound create/bind/insert fixtures,
 * or a probe curated value that stays pending until a later confirmation.
 */
public enum CuratedDraftItemKind {
    RUNS_ON_TARGET_CHANGE,
    CREATE_CONTAINER_FROM_UNBOUND,
    BIND_UNBOUND_TO_EXISTING,
    CURATED_RUNS_ON_INSERT,
    /** Probe 改理想. Opening the draft does not write the task's curated value. */
    PROBE_CURATED_VALUE
}
