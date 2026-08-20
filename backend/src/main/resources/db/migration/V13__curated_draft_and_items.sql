-- Ticket 03: 改理想开放草案 + 条目（确认前不是策展真相）。Postgres is SSOT.

CREATE TABLE curated_draft (
    id                 TEXT PRIMARY KEY,
    conflict_id        TEXT NOT NULL REFERENCES conflict_case (id),
    diagnosis_id       TEXT NOT NULL REFERENCES conflict_diagnosis (id),
    selected_fork_id   TEXT NOT NULL,
    status             TEXT NOT NULL CHECK (status IN ('OPEN', 'VOIDED')),
    created_by         TEXT NOT NULL REFERENCES platform_user (id),
    created_at         TIMESTAMPTZ NOT NULL
);

-- At most one open 草案 per 冲突.
CREATE UNIQUE INDEX curated_draft_open_conflict_uq
    ON curated_draft (conflict_id)
    WHERE status = 'OPEN';

CREATE INDEX curated_draft_conflict_idx ON curated_draft (conflict_id);

CREATE TABLE curated_draft_item (
    id              TEXT PRIMARY KEY,
    draft_id        TEXT NOT NULL REFERENCES curated_draft (id),
    seq             INT NOT NULL,
    kind            TEXT NOT NULL CHECK (kind IN ('RUNS_ON_TARGET_CHANGE')),
    status          TEXT NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED')),
    subject_id      TEXT NOT NULL REFERENCES curated_object (id),
    from_host_id    TEXT NOT NULL REFERENCES curated_object (id),
    to_host_id      TEXT NOT NULL REFERENCES curated_object (id),
    payload_json    TEXT NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT curated_draft_item_draft_seq_uq UNIQUE (draft_id, seq)
);

CREATE INDEX curated_draft_item_draft_idx ON curated_draft_item (draft_id);

DO $$
DECLARE
    cname text;
BEGIN
    FOR cname IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = 'public'
          AND rel.relname = 'conflict_case_event'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%event_type%'
    LOOP
        EXECUTE format('ALTER TABLE conflict_case_event DROP CONSTRAINT %I', cname);
    END LOOP;
END $$;

ALTER TABLE conflict_case_event
    ADD CONSTRAINT conflict_case_event_type_check
        CHECK (event_type IN (
            'WARNED',
            'UPGRADED',
            'ACKNOWLEDGED',
            'HANDLER_ASSIGNED',
            'HANDLER_ACCEPTED',
            'HANDLER_REJECTED',
            'HANDLER_TRANSFER_OFFERED',
            'PLAN_COMPLETED',
            'PENDING_CLOSE',
            'CONFIRM_FAILED',
            'CLOSED',
            'SUSPENDED',
            'PLAN_VOIDED',
            'DRAFT_CREATED'
        ));
