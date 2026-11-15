CREATE TABLE alert (
    id uuid PRIMARY KEY,
    host_id uuid NOT NULL REFERENCES host (id),
    role text NOT NULL,
    current boolean NOT NULL,
    evidence text NOT NULL
);
