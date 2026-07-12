-- SSH connection pool support: conversation-level target assets for AI context

ALTER TABLE ai_conversations
    ADD COLUMN IF NOT EXISTS target_asset_ids JSONB NOT NULL DEFAULT '[]'::jsonb;
