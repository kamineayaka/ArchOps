-- CloudOps AI Platform - Dynamic AI providers and platform settings

CREATE TABLE ai_provider (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(64)  NOT NULL,
    provider_type       VARCHAR(32)  NOT NULL,
    base_url            VARCHAR(512),
    api_key_cipher      BYTEA,
    api_key_iv          BYTEA,
    chat_model          VARCHAR(128),
    embedding_model     VARCHAR(128),
    embedding_dims      INTEGER,
    supports_chat       BOOLEAN      NOT NULL DEFAULT TRUE,
    supports_embedding  BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    timeout_ms          BIGINT       NOT NULL DEFAULT 60000,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_provider_enabled ON ai_provider(enabled);

CREATE TABLE platform_ai_settings (
    id                            SMALLINT PRIMARY KEY DEFAULT 1,
    default_chat_provider_id      BIGINT REFERENCES ai_provider(id) ON DELETE SET NULL,
    default_embedding_provider_id BIGINT REFERENCES ai_provider(id) ON DELETE SET NULL,
    rag_enabled                   BOOLEAN NOT NULL DEFAULT TRUE,
    rag_top_k                     INTEGER NOT NULL DEFAULT 5,
    rag_min_similarity            DOUBLE PRECISION NOT NULL DEFAULT 0.35,
    CONSTRAINT platform_ai_settings_single_row CHECK (id = 1)
);

INSERT INTO platform_ai_settings (id) VALUES (1);
