-- Graph SSOT W1: asset graph anchor, soft-delete, graph_meta, layout prefs

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE assets
    ADD COLUMN IF NOT EXISTS element_id UUID,
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS delete_reason VARCHAR(512),
    ADD COLUMN IF NOT EXISTS graph_synced_at TIMESTAMPTZ;

UPDATE assets
SET element_id = gen_random_uuid()
WHERE element_id IS NULL;

ALTER TABLE assets
    ALTER COLUMN element_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_assets_element_id ON assets (element_id);

CREATE INDEX IF NOT EXISTS idx_assets_active
    ON assets (kind)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS graph_meta (
    key              VARCHAR(64) PRIMARY KEY,
    graph_version    BIGINT       NOT NULL DEFAULT 0,
    neo4j_bookmark   TEXT,
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO graph_meta (key, graph_version)
VALUES ('global', 0)
ON CONFLICT (key) DO NOTHING;

CREATE TABLE IF NOT EXISTS user_graph_layout (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    scope_key    VARCHAR(128) NOT NULL,
    layout_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_graph_layout UNIQUE (user_id, scope_key)
);

CREATE TABLE IF NOT EXISTS graph_saved_view (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    scope_key     VARCHAR(128) NOT NULL,
    cypher_read   TEXT         NOT NULL,
    created_by    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_graph_saved_view_scope ON graph_saved_view (scope_key);
