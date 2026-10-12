-- ADR-0048: control plane stores the judgment of a returned probe result.
-- NORMAL is a readable value equal to the curated structured value. No cause-kind column.

CREATE TABLE probe_judgment (
    task_id        TEXT PRIMARY KEY REFERENCES probe_task (id),
    outcome        TEXT NOT NULL CHECK (outcome IN ('NORMAL', 'CONFLICT', 'HOLLOW')),
    observed_json  TEXT,
    evidence       TEXT,
    conflict_id    TEXT,
    judged_at      TIMESTAMPTZ NOT NULL
);
