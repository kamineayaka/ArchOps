package com.archops.curated.domain;

/**
 * Draft item kinds: change-curated 运行于 retarget, or unbound create/bind/insert fixtures.
 */
public enum CuratedDraftItemKind {
    RUNS_ON_TARGET_CHANGE,
    CREATE_CONTAINER_FROM_UNBOUND,
    BIND_UNBOUND_TO_EXISTING,
    CURATED_RUNS_ON_INSERT
}
