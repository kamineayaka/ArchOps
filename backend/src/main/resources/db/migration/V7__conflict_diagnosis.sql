-- Conflict diagnosis results (ticket 06). Jobs are queued in Redis; Postgres remains SSOT.

CREATE TABLE conflict_diagnosis (
    id              TEXT PRIMARY KEY,
    conflict_id     TEXT NOT NULL REFERENCES conflict_case (id),
    status          TEXT NOT NULL CHECK (status IN ('PENDING', 'READY', 'FAILED', 'STALE')),
    source          TEXT CHECK (source IS NULL OR source IN ('RULES', 'RULES_WITH_LLM', 'RULES_LLM_FALLBACK')),
    summary         TEXT,
    forks_json      TEXT NOT NULL DEFAULT '[]',
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX conflict_diagnosis_conflict_created_idx
    ON conflict_diagnosis (conflict_id, created_at DESC);

CREATE INDEX conflict_diagnosis_status_idx
    ON conflict_diagnosis (status);
