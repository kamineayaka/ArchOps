-- Ticket 04: 草案条目已接受（含写入）/ 已拒绝 audit. Do not edit V13.

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
            'DRAFT_CREATED',
            'DRAFT_ITEM_ACCEPTED',
            'DRAFT_ITEM_REJECTED'
        ));
