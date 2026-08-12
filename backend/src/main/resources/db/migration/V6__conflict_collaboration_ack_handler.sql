-- Conflict collaboration: 已知悉 / 归属 / 处理人 (ticket 05 Must).
-- Assign/reject/transfer (PENDING_ACCEPT) lands in ticket 11.

ALTER TABLE conflict_case
    ADD COLUMN acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN acknowledged_at TIMESTAMPTZ,
    ADD COLUMN owner_user_id TEXT REFERENCES platform_user (id),
    ADD COLUMN handler_user_id TEXT REFERENCES platform_user (id),
    ADD COLUMN handler_acceptance TEXT NOT NULL DEFAULT 'NONE'
        CHECK (handler_acceptance IN ('NONE', 'PENDING_ACCEPT', 'ACCEPTED'));

ALTER TABLE conflict_case
    ADD CONSTRAINT conflict_case_ack_owner_chk CHECK (
        (acknowledged = FALSE AND owner_user_id IS NULL AND acknowledged_at IS NULL)
        OR (acknowledged = TRUE AND owner_user_id IS NOT NULL AND acknowledged_at IS NOT NULL)
    );

ALTER TABLE conflict_case
    ADD CONSTRAINT conflict_case_handler_acceptance_chk CHECK (
        (handler_acceptance = 'NONE' AND handler_user_id IS NULL)
        OR (handler_acceptance IN ('PENDING_ACCEPT', 'ACCEPTED') AND handler_user_id IS NOT NULL)
    );
