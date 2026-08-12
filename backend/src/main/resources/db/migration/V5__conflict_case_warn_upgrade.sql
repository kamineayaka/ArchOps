-- Conflict warn + merge-key upgrade (ticket 04). Diagnosis is out of scope.

CREATE TABLE conflict_case (
    id                      TEXT PRIMARY KEY,
    subject_id              TEXT NOT NULL REFERENCES curated_object (id),
    relation_type           TEXT NOT NULL CHECK (relation_type IN ('RUNS_ON')),
    status                  TEXT NOT NULL CHECK (status IN ('OPEN')),
    curated_target_id       TEXT NOT NULL REFERENCES curated_object (id),
    observed_availability   TEXT NOT NULL CHECK (observed_availability IN ('PRESENT', 'ABSENT')),
    observed_target_id      TEXT REFERENCES curated_object (id),
    -- JSON array of observed value steps for upgrade lineage, e.g. A→B→C trail of actuals.
    observed_lineage_json   TEXT NOT NULL DEFAULT '[]',
    first_warned_at         TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT conflict_case_observed_target_chk CHECK (
        (observed_availability = 'PRESENT' AND observed_target_id IS NOT NULL)
        OR (observed_availability = 'ABSENT' AND observed_target_id IS NULL)
    )
);

-- One active open conflict per merge key (object + relation type).
CREATE UNIQUE INDEX conflict_case_open_merge_key_uq
    ON conflict_case (subject_id, relation_type)
    WHERE status = 'OPEN';

CREATE INDEX conflict_case_status_idx ON conflict_case (status);
