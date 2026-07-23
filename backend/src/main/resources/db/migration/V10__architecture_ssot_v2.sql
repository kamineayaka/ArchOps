-- ML-2/ML-3/ML-5: Architecture SSOT v2, proposals, work_log binding

CREATE TABLE IF NOT EXISTS architecture_partition (
    id            BIGSERIAL PRIMARY KEY,
    partition_key VARCHAR(128) NOT NULL,
    title         VARCHAR(255),
    high_impact   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_architecture_partition_key UNIQUE (partition_key)
);

CREATE TABLE IF NOT EXISTS architecture_revision (
    id               BIGSERIAL PRIMARY KEY,
    partition_id     BIGINT       NOT NULL REFERENCES architecture_partition(id) ON DELETE CASCADE,
    version          BIGINT       NOT NULL,
    summary          TEXT,
    body_md          TEXT,
    structured_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_by       BIGINT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_architecture_revision_version UNIQUE (partition_id, version)
);

CREATE INDEX IF NOT EXISTS idx_arch_revision_partition ON architecture_revision(partition_id, version DESC);

CREATE TABLE IF NOT EXISTS architecture_fact (
    id               BIGSERIAL PRIMARY KEY,
    partition_id     BIGINT       NOT NULL REFERENCES architecture_partition(id) ON DELETE CASCADE,
    revision_id      BIGINT       REFERENCES architecture_revision(id) ON DELETE SET NULL,
    fact_type        VARCHAR(32)  NOT NULL,
    subject          VARCHAR(255) NOT NULL,
    predicate        VARCHAR(128) NOT NULL,
    object           VARCHAR(512) NOT NULL,
    asset_id         BIGINT,
    confidence       DOUBLE PRECISION,
    status           VARCHAR(32)  NOT NULL DEFAULT 'active',
    provenance_json  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_arch_fact_partition ON architecture_fact(partition_id, status);
CREATE INDEX IF NOT EXISTS idx_arch_fact_asset ON architecture_fact(asset_id);

CREATE TABLE IF NOT EXISTS architecture_proposal (
    id                   BIGSERIAL PRIMARY KEY,
    partition_key        VARCHAR(128) NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    summary              TEXT,
    diff_json            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    fact_ops             JSONB        NOT NULL DEFAULT '[]'::jsonb,
    evidence             JSONB        NOT NULL DEFAULT '[]'::jsonb,
    risk                 VARCHAR(16),
    confidence           DOUBLE PRECISION,
    requester_id         BIGINT       NOT NULL,
    reviewer_id          BIGINT,
    conversation_id      BIGINT,
    base_version         BIGINT       NOT NULL,
    related_approval_id  BIGINT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    decided_at           TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_arch_proposal_status ON architecture_proposal(status);
CREATE INDEX IF NOT EXISTS idx_arch_proposal_partition ON architecture_proposal(partition_key);

-- Migrate legacy architecture_snapshot into global partition
INSERT INTO architecture_partition (partition_key, title, high_impact)
VALUES ('global', 'Global architecture', TRUE)
ON CONFLICT (partition_key) DO NOTHING;

INSERT INTO architecture_revision (partition_id, version, summary, body_md, structured_json, created_by, created_at)
SELECT p.id,
       s.version,
       s.summary,
       COALESCE(s.content::text, ''),
       CASE WHEN jsonb_typeof(s.content) = 'object' THEN s.content ELSE '{}'::jsonb END,
       NULL,
       s.created_at
FROM architecture_snapshot s
CROSS JOIN architecture_partition p
WHERE p.partition_key = 'global'
  AND NOT EXISTS (
      SELECT 1 FROM architecture_revision r
      WHERE r.partition_id = p.id AND r.version = s.version
  );

-- Ensure at least one empty global revision
INSERT INTO architecture_revision (partition_id, version, summary, body_md, structured_json)
SELECT p.id, 1, 'Initial global architecture', '', '{}'::jsonb
FROM architecture_partition p
WHERE p.partition_key = 'global'
  AND NOT EXISTS (SELECT 1 FROM architecture_revision r WHERE r.partition_id = p.id);

ALTER TABLE work_log
    ADD COLUMN IF NOT EXISTS conversation_id BIGINT,
    ADD COLUMN IF NOT EXISTS user_id BIGINT,
    ADD COLUMN IF NOT EXISTS asset_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS group_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS level VARCHAR(8),
    ADD COLUMN IF NOT EXISTS hypothesis BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS source VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_work_log_conversation ON work_log(conversation_id);
CREATE INDEX IF NOT EXISTS idx_work_log_level ON work_log(level);

CREATE TABLE IF NOT EXISTS tool_execution_event (
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT,
    user_id          BIGINT,
    tool_name        VARCHAR(128) NOT NULL,
    arguments_json   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    stdout_summary   TEXT,
    stderr_summary   TEXT,
    exit_code        INTEGER,
    asset_ids        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    level            VARCHAR(8),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tool_exec_conversation ON tool_execution_event(conversation_id);
