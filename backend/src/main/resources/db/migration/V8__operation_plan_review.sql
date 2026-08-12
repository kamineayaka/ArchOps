-- Operation plan binding + human review state machine (ticket 07). SSH execute is ticket 08.

CREATE TABLE operation_plan (
    id                  TEXT PRIMARY KEY,
    conflict_id         TEXT NOT NULL REFERENCES conflict_case (id),
    diagnosis_id        TEXT NOT NULL REFERENCES conflict_diagnosis (id),
    selected_fork_id    TEXT NOT NULL,
    branch_kind         TEXT NOT NULL CHECK (branch_kind IN ('FIX_ACTUAL')),
    skips_draft         BOOLEAN NOT NULL DEFAULT TRUE,
    status              TEXT NOT NULL CHECK (status IN (
                            'DRAFT_REVIEW',
                            'APPROVED',
                            'EXECUTING',
                            'COMPLETED',
                            'VOIDED',
                            'SUPERSEDED'
                        )),
    steps_json          TEXT NOT NULL,
    created_by          TEXT NOT NULL REFERENCES platform_user (id),
    created_at          TIMESTAMPTZ NOT NULL,
    reviewed_by         TEXT REFERENCES platform_user (id),
    reviewed_at         TIMESTAMPTZ,
    approved_at         TIMESTAMPTZ
);

-- At most one active operation plan per conflict.
CREATE UNIQUE INDEX operation_plan_active_conflict_uq
    ON operation_plan (conflict_id)
    WHERE status IN ('DRAFT_REVIEW', 'APPROVED', 'EXECUTING');

CREATE INDEX operation_plan_conflict_idx ON operation_plan (conflict_id);
