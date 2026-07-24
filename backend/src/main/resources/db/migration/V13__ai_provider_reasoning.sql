-- W3: OpsKat-aligned AI Provider generation / reasoning fields
ALTER TABLE ai_provider
    ADD COLUMN IF NOT EXISTS max_output_tokens INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS context_window INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reasoning_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reasoning_effort VARCHAR(16) NOT NULL DEFAULT 'NONE';

COMMENT ON COLUMN ai_provider.max_output_tokens IS '0 = provider default';
COMMENT ON COLUMN ai_provider.context_window IS '0 = unlimited / default; approximate tokens for context budget';
COMMENT ON COLUMN ai_provider.reasoning_effort IS 'NONE|LOW|MEDIUM|HIGH|XHIGH|MAX (MAX Anthropic-only)';
