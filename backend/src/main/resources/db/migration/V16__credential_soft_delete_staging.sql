-- Graph SSOT W3: credential soft-delete + staging vault (no secrets in proposals)

ALTER TABLE ssh_credentials
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS superseded_by BIGINT REFERENCES ssh_credentials(id) ON DELETE SET NULL;

DROP INDEX IF EXISTS idx_ssh_cred_asset;

CREATE UNIQUE INDEX IF NOT EXISTS idx_ssh_cred_asset_active
    ON ssh_credentials (asset_id)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS credential_staging (
    id                   UUID PRIMARY KEY,
    requester_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    proposal_id          BIGINT       REFERENCES architecture_proposal(id) ON DELETE SET NULL,
    asset_id             BIGINT       REFERENCES assets(id) ON DELETE SET NULL,
    temp_ref             VARCHAR(64),
    username             VARCHAR(64)  NOT NULL,
    auth_type            VARCHAR(16)  NOT NULL,
    secret_cipher        BYTEA        NOT NULL,
    secret_iv            BYTEA        NOT NULL,
    passphrase_hash      VARCHAR(255),
    expires_at           TIMESTAMPTZ  NOT NULL,
    consumed_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_credential_staging_expires
    ON credential_staging (expires_at);

CREATE INDEX IF NOT EXISTS idx_credential_staging_proposal
    ON credential_staging (proposal_id);
