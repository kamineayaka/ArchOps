-- ADR-0048 ticket 01: probe tasks are control-plane data.
-- The curated structured value is the only configured normal.

CREATE TABLE probe_task (
    id                TEXT PRIMARY KEY,
    host_id           TEXT NOT NULL REFERENCES curated_object (id),
    command           TEXT NOT NULL,
    expected_json     TEXT NOT NULL,
    interval_seconds  INTEGER NOT NULL CHECK (interval_seconds >= 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX probe_task_host_id_idx ON probe_task (host_id);
