-- Graph SSOT W4: terminal session dock (multi-device sync)

CREATE TABLE IF NOT EXISTS terminal_session_dock (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    element_id      UUID         NOT NULL,
    asset_id        BIGINT       NOT NULL REFERENCES assets(id) ON DELETE CASCADE,
    pinned          BOOLEAN      NOT NULL DEFAULT FALSE,
    last_opened_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_terminal_session_dock UNIQUE (user_id, element_id)
);

CREATE INDEX IF NOT EXISTS idx_terminal_dock_user_opened
    ON terminal_session_dock (user_id, pinned DESC, last_opened_at DESC);
