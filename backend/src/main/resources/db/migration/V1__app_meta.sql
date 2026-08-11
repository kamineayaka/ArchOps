-- Scaffold bootstrap only. Domain tables land in the vertical-slice conversation.
CREATE TABLE app_meta (
    meta_key   TEXT PRIMARY KEY,
    meta_value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO app_meta (meta_key, meta_value)
VALUES ('schema_bootstrap', 'v1');
