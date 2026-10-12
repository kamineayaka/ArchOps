-- ADR-0048: a readable probe value that differs from the curated value is a conflict.
-- Evidence is raw text. There is no cause-kind column.
-- Probe conflicts are keyed by task so two tasks on one host stay independent.

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
          AND pg_get_constraintdef(con.oid) ILIKE '%relation_type%'
          AND pg_get_constraintdef(con.oid) ILIKE '%RUNS_ON%'
    LOOP
        EXECUTE format('ALTER TABLE conflict_case DROP CONSTRAINT %I', cname);
    END LOOP;
END $$;

ALTER TABLE conflict_case
    ADD CONSTRAINT conflict_case_relation_type_check
        CHECK (relation_type IN ('RUNS_ON', 'PROBE'));

ALTER TABLE conflict_case
    ADD COLUMN probe_task_id TEXT,
    ADD COLUMN evidence TEXT,
    ADD COLUMN curated_value_json TEXT,
    ADD COLUMN observed_value_json TEXT;

DROP INDEX IF EXISTS conflict_case_active_merge_key_uq;

CREATE UNIQUE INDEX conflict_case_active_merge_key_uq
    ON conflict_case (subject_id, relation_type)
    WHERE status IN ('OPEN', 'PENDING_CLOSE', 'SUSPENDED')
      AND probe_task_id IS NULL;

CREATE UNIQUE INDEX conflict_case_active_probe_task_uq
    ON conflict_case (probe_task_id)
    WHERE probe_task_id IS NOT NULL
      AND status IN ('OPEN', 'PENDING_CLOSE', 'SUSPENDED');
