-- Ticket 10: heartbeat timeout → observation hollow → conflict SUSPENDED + void plans.

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
    LOOP
        EXECUTE format('ALTER TABLE conflict_case DROP CONSTRAINT %I', cname);
    END LOOP;
END $$;

ALTER TABLE conflict_case
    ADD CONSTRAINT conflict_case_status_check
        CHECK (status IN ('OPEN', 'PENDING_CLOSE', 'CLOSED', 'SUSPENDED'));

ALTER TABLE conflict_case
    ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMPTZ;

DROP INDEX IF EXISTS conflict_case_active_merge_key_uq;

CREATE UNIQUE INDEX conflict_case_active_merge_key_uq
    ON conflict_case (subject_id, relation_type)
    WHERE status IN ('OPEN', 'PENDING_CLOSE', 'SUSPENDED');

-- Widen conflict_case_event.event_type CHECK to include SUSPENDED / PLAN_VOIDED.
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
            'HANDLER_ACCEPTED',
            'PLAN_COMPLETED',
            'PENDING_CLOSE',
            'CONFIRM_FAILED',
            'CLOSED',
            'SUSPENDED',
            'PLAN_VOIDED'
        ));
