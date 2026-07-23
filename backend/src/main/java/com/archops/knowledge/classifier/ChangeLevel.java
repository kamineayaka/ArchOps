package com.archops.knowledge.classifier;

/** Change impact level for tool executions and work logs. */
public enum ChangeLevel {
    /** Read-only diagnostics; no architecture write. */
    L0,
    /** Architecture discovery / soft change; propose update. */
    L1,
    /** High-impact / mutating change; propose (auto-draft if model skips). */
    L2
}
