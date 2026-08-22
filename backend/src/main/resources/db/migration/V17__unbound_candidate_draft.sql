-- Ticket 02: unbound-candidate drafts on curated_draft (no dummy conflict).
-- Forward-only: do not edit V13–V16.

ALTER TABLE curated_draft
    ADD COLUMN origin TEXT NOT NULL DEFAULT 'CHANGE_CURATED',
    ADD COLUMN candidate_id TEXT NULL,
    ADD COLUMN source_host_id TEXT NULL,
    ADD COLUMN runtime_id TEXT NULL;

ALTER TABLE curated_draft
    ALTER COLUMN conflict_id DROP NOT NULL,
    ALTER COLUMN diagnosis_id DROP NOT NULL,
    ALTER COLUMN selected_fork_id DROP NOT NULL;

ALTER TABLE curated_draft
    ADD CONSTRAINT curated_draft_origin_check
        CHECK (origin IN ('CHANGE_CURATED', 'UNBOUND_CANDIDATE'));

ALTER TABLE curated_draft
    ADD CONSTRAINT curated_draft_change_curated_refs_check
        CHECK (
            origin <> 'CHANGE_CURATED'
            OR (
                conflict_id IS NOT NULL
                AND diagnosis_id IS NOT NULL
                AND selected_fork_id IS NOT NULL
            )
        );

ALTER TABLE curated_draft
    ADD CONSTRAINT curated_draft_unbound_refs_check
        CHECK (
            origin <> 'UNBOUND_CANDIDATE'
            OR (
                conflict_id IS NULL
                AND diagnosis_id IS NULL
                AND selected_fork_id IS NULL
                AND candidate_id IS NOT NULL
            )
        );

ALTER TABLE curated_draft
    ADD CONSTRAINT curated_draft_candidate_fk
        FOREIGN KEY (candidate_id) REFERENCES unbound_observation_candidate (id);

-- One OPEN unbound draft per candidate (field entity).
CREATE UNIQUE INDEX curated_draft_open_unbound_candidate_uq
    ON curated_draft (candidate_id)
    WHERE status = 'OPEN' AND origin = 'UNBOUND_CANDIDATE';

-- Keep “one OPEN per conflict” for change-curated (conflict_id NOT NULL).
-- Existing curated_draft_open_conflict_uq remains valid for non-null conflict_id.

ALTER TABLE curated_draft_item
    ALTER COLUMN subject_id DROP NOT NULL,
    ALTER COLUMN from_host_id DROP NOT NULL,
    ALTER COLUMN to_host_id DROP NOT NULL;

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
          AND rel.relname = 'curated_draft_item'
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%kind%'
    LOOP
        EXECUTE format('ALTER TABLE curated_draft_item DROP CONSTRAINT %I', cname);
    END LOOP;
END $$;

ALTER TABLE curated_draft_item
    ADD CONSTRAINT curated_draft_item_kind_check
        CHECK (kind IN (
            'RUNS_ON_TARGET_CHANGE',
            'CREATE_CONTAINER_FROM_UNBOUND',
            'BIND_UNBOUND_TO_EXISTING',
            'CURATED_RUNS_ON_INSERT'
        ));

CREATE TABLE curated_draft_event (
    id              TEXT PRIMARY KEY,
    draft_id        TEXT NOT NULL REFERENCES curated_draft (id),
    event_type      TEXT NOT NULL,
    actor_user_id   TEXT NULL REFERENCES platform_user (id),
    detail_json     TEXT NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX curated_draft_event_draft_idx ON curated_draft_event (draft_id);
