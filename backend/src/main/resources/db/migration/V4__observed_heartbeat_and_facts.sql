-- Observed truth + agent heartbeat freshness (ticket 03).
-- Postgres remains SSOT; Redis is not used for relationship truth.

CREATE TABLE host_agent (
    agent_id            TEXT PRIMARY KEY,
    host_id             TEXT NOT NULL REFERENCES curated_object (id),
    last_heartbeat_at   TIMESTAMPTZ NOT NULL,
    last_snapshot_at    TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX host_agent_host_id_idx ON host_agent (host_id);

CREATE TABLE observed_fact (
    id                  TEXT PRIMARY KEY,
    subject_id          TEXT NOT NULL REFERENCES curated_object (id),
    relation_type       TEXT NOT NULL CHECK (relation_type IN ('RUNS_ON')),
    -- PRESENT: target_id is the observed host; ABSENT: 观测消失 (available value = does not exist)
    availability        TEXT NOT NULL CHECK (availability IN ('PRESENT', 'ABSENT')),
    target_id           TEXT REFERENCES curated_object (id),
    observed_at         TIMESTAMPTZ NOT NULL,
    source_agent_id     TEXT NOT NULL,
    source_host_id      TEXT NOT NULL REFERENCES curated_object (id),
    CONSTRAINT observed_fact_subject_relation_uq UNIQUE (subject_id, relation_type),
    CONSTRAINT observed_fact_target_chk CHECK (
        (availability = 'PRESENT' AND target_id IS NOT NULL)
        OR (availability = 'ABSENT' AND target_id IS NULL)
    )
);

CREATE INDEX observed_fact_target_idx ON observed_fact (target_id);

CREATE TABLE unbound_observation_candidate (
    id                      TEXT PRIMARY KEY,
    source_agent_id         TEXT NOT NULL,
    source_host_id          TEXT NOT NULL REFERENCES curated_object (id),
    runtime_id              TEXT,
    name                    TEXT,
    labels_json             TEXT NOT NULL DEFAULT '{}',
    reason                  TEXT NOT NULL CHECK (reason IN ('MISSING_LABEL', 'UNKNOWN_OBJECT_ID')),
    upgrade_chain_promised  BOOLEAN NOT NULL DEFAULT FALSE,
    observed_at             TIMESTAMPTZ NOT NULL
);

CREATE INDEX unbound_observation_host_idx ON unbound_observation_candidate (source_host_id);

CREATE TABLE identity_lost_mark (
    curated_object_id   TEXT PRIMARY KEY REFERENCES curated_object (id),
    reason              TEXT NOT NULL,
    marked_at           TIMESTAMPTZ NOT NULL,
    source_agent_id     TEXT NOT NULL,
    source_host_id      TEXT NOT NULL REFERENCES curated_object (id),
    upgrade_chain_promised BOOLEAN NOT NULL DEFAULT FALSE
);
