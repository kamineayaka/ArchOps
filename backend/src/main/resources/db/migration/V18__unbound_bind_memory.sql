-- Ticket 03: bind memory after accepted BIND/CREATE (matching state, not a 冲突).
-- Forward-only: do not edit V1–V17.

CREATE TABLE unbound_bind_memory (
    id                  TEXT PRIMARY KEY,
    source_host_id      TEXT NOT NULL REFERENCES curated_object (id),
    runtime_id          TEXT NOT NULL,
    curated_object_id   TEXT NOT NULL REFERENCES curated_object (id),
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT unbound_bind_memory_host_runtime_uq UNIQUE (source_host_id, runtime_id)
);

CREATE INDEX unbound_bind_memory_object_idx ON unbound_bind_memory (curated_object_id);
