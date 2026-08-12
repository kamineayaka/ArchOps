-- Curated truth: physical hosts, Docker containers, and RUNS_ON facts (ticket 02).
-- Graph semantics in Postgres only; no Neo4j.

CREATE TABLE curated_object (
    id                   TEXT PRIMARY KEY,
    kind                 TEXT NOT NULL CHECK (kind IN ('PHYSICAL_HOST', 'DOCKER_CONTAINER')),
    name                 TEXT NOT NULL,
    -- Immutable identity for Docker containers (label value of archops.object_id=<value>).
    immutable_object_id  TEXT,
    created_by           TEXT NOT NULL REFERENCES platform_user (id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT curated_object_kind_object_id_chk CHECK (
        (kind = 'DOCKER_CONTAINER' AND immutable_object_id IS NOT NULL AND btrim(immutable_object_id) <> '')
        OR (kind = 'PHYSICAL_HOST' AND immutable_object_id IS NULL)
    )
);

CREATE UNIQUE INDEX curated_object_immutable_object_id_uq
    ON curated_object (immutable_object_id)
    WHERE immutable_object_id IS NOT NULL;

CREATE TABLE curated_fact (
    id                 TEXT PRIMARY KEY,
    subject_id         TEXT NOT NULL REFERENCES curated_object (id),
    relation_type      TEXT NOT NULL CHECK (relation_type IN ('RUNS_ON')),
    target_id          TEXT NOT NULL REFERENCES curated_object (id),
    created_by         TEXT NOT NULL REFERENCES platform_user (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT curated_fact_subject_relation_uq UNIQUE (subject_id, relation_type)
);

CREATE INDEX curated_fact_target_idx ON curated_fact (target_id);
