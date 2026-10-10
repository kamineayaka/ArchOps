-- Collaboration identity is not a conflict fact. The HTTP routes are gone;
-- these columns must not remain as 已知悉 / 归属 / 处理人 storage.

ALTER TABLE conflict_case
    DROP COLUMN acknowledged,
    DROP COLUMN acknowledged_at,
    DROP COLUMN owner_user_id,
    DROP COLUMN handler_user_id,
    DROP COLUMN handler_acceptance;
