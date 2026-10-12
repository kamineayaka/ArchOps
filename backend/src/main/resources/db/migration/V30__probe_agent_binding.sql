-- ADR-0048: at most one host agent executes probe tasks on a host.
-- Hosts with no probe agent stay valid; this table simply has no row.

CREATE TABLE probe_agent_binding (
    host_id   TEXT PRIMARY KEY REFERENCES curated_object (id),
    agent_id  TEXT NOT NULL,
    bound_at  TIMESTAMPTZ NOT NULL
);
