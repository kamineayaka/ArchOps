CREATE TABLE repair (
    id uuid PRIMARY KEY,
    alert_id uuid NOT NULL UNIQUE REFERENCES alert (id),
    host_id uuid NOT NULL REFERENCES host (id),
    role text NOT NULL,
    status text NOT NULL CHECK (status IN ('open', 'stopped', 'completed')),
    playbook jsonb NOT NULL,
    issued_seq integer NOT NULL
);

CREATE UNIQUE INDEX repair_one_open_per_host
    ON repair (host_id)
    WHERE status = 'open';

CREATE TABLE repair_step (
    repair_id uuid NOT NULL REFERENCES repair (id),
    seq integer NOT NULL,
    argv jsonb NOT NULL,
    state text NOT NULL CHECK (state IN ('issued', 'succeeded', 'failed')),
    exit_code integer,
    output text,
    PRIMARY KEY (repair_id, seq)
);
