-- Ticket 08: plan execution progress + encrypted host SSH credentials (MINA path).

ALTER TABLE operation_plan
    ADD COLUMN current_step_seq INT,
    ADD COLUMN void_reason TEXT,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN finished_at TIMESTAMPTZ,
    ADD COLUMN execution_log_json TEXT;

-- Graph-resident physical host credentials. Ciphertext only — never store plaintext secrets.
CREATE TABLE host_ssh_credential (
    host_id           TEXT PRIMARY KEY REFERENCES curated_object (id),
    connect_host      TEXT NOT NULL,
    connect_port      INT NOT NULL DEFAULT 22,
    username          TEXT NOT NULL,
    secret_ciphertext TEXT NOT NULL,
    secret_kind       TEXT NOT NULL CHECK (secret_kind IN ('PASSWORD', 'PRIVATE_KEY')),
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX host_ssh_credential_updated_idx ON host_ssh_credential (updated_at);
