-- Ticket 09: pending close / confirm close + minimal conflict audit events.

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
          AND rel.relname = 'conflict_case'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%status%'
          AND pg_get_constraintdef(con.oid) ILIKE '%OPEN%'
    LOOP
        EXECUTE format('ALTER TABLE conflict_case DROP CONSTRAINT %I', cname);
    END LOOP;
END $$;

ALTER TABLE conflict_case
    ADD CONSTRAINT conflict_case_status_check
        CHECK (status IN ('OPEN', 'PENDING_CLOSE', 'CLOSED'));

ALTER TABLE conflict_case
    ADD COLUMN IF NOT EXISTS pending_close_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;

DROP INDEX IF EXISTS conflict_case_open_merge_key_uq;

-- At most one active (not closed) conflict per merge key.
CREATE UNIQUE INDEX IF NOT EXISTS conflict_case_active_merge_key_uq
    ON conflict_case (subject_id, relation_type)
    WHERE status IN ('OPEN', 'PENDING_CLOSE');

CREATE TABLE IF NOT EXISTS conflict_case_event (
    id            TEXT PRIMARY KEY,
    conflict_id   TEXT NOT NULL REFERENCES conflict_case (id),
    event_type    TEXT NOT NULL CHECK (event_type IN (
                      'WARNED',
                      'UPGRADED',
                      'ACKNOWLEDGED',
                      'HANDLER_ACCEPTED',
                      'PLAN_COMPLETED',
                      'PENDING_CLOSE',
                      'CONFIRM_FAILED',
                      'CLOSED'
                  )),
    actor_user_id TEXT REFERENCES platform_user (id),
    detail_json   TEXT NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS conflict_case_event_conflict_idx
    ON conflict_case_event (conflict_id, created_at);
