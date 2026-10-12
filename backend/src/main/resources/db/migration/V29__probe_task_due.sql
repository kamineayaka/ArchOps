-- ADR-0048: a pulled task that is not returned by its due time is 观测空洞 for that task only.

ALTER TABLE probe_task
    ADD COLUMN due_at TIMESTAMPTZ,
    ADD COLUMN awaiting_result BOOLEAN NOT NULL DEFAULT FALSE;
