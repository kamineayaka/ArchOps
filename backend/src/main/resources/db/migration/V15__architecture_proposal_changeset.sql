-- Graph SSOT W2: proposal ChangeSet, scope reinterpretation, migration map

ALTER TABLE architecture_proposal
    ADD COLUMN IF NOT EXISTS scope_kind VARCHAR(32),
    ADD COLUMN IF NOT EXISTS scope_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS change_set JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS plan_json JSONB,
    ADD COLUMN IF NOT EXISTS base_graph_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS merged_graph_version BIGINT,
    ADD COLUMN IF NOT EXISTS conflict_detail JSONB;

-- partition_key is reinterpreted as scope key (no separate scope_key column)
UPDATE architecture_partition
SET partition_key = 'graph:global',
    title = COALESCE(title, 'Global graph'),
    updated_at = NOW()
WHERE partition_key = 'global';

UPDATE architecture_proposal
SET partition_key = 'graph:global'
WHERE partition_key = 'global';

UPDATE architecture_proposal
SET scope_kind = CASE
        WHEN partition_key = 'graph:global' OR partition_key = 'global' THEN 'graph'
        WHEN partition_key LIKE 'cluster:%' THEN 'cluster'
        WHEN partition_key LIKE 'tag:%' THEN 'tag'
        WHEN partition_key LIKE 'view:%' THEN 'view'
        WHEN partition_key LIKE 'asset:%' THEN 'asset'
        WHEN partition_key LIKE 'group:%' THEN 'tag'
        ELSE 'graph'
    END
WHERE scope_kind IS NULL;

ALTER TABLE architecture_revision
    ADD COLUMN IF NOT EXISTS graph_version BIGINT,
    ADD COLUMN IF NOT EXISTS change_set_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS proposal_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_arch_proposal_base_graph_version
    ON architecture_proposal (base_graph_version);

CREATE INDEX IF NOT EXISTS idx_arch_proposal_scope_kind
    ON architecture_proposal (scope_kind);

CREATE TABLE IF NOT EXISTS graph_migration_map (
    id               BIGSERIAL PRIMARY KEY,
    source_type      VARCHAR(32)  NOT NULL,
    source_key       VARCHAR(128) NOT NULL,
    target_kind      VARCHAR(32)  NOT NULL,
    element_id       UUID,
    rel_type         VARCHAR(32),
    from_element_id  UUID,
    to_element_id    UUID,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    detail           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_graph_migration_map UNIQUE (source_type, source_key)
);

CREATE INDEX IF NOT EXISTS idx_graph_migration_map_status
    ON graph_migration_map (status);
