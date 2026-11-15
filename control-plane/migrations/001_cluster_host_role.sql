CREATE TABLE IF NOT EXISTS cluster (
    id uuid PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE IF NOT EXISTS host (
    id uuid PRIMARY KEY,
    cluster_id uuid NOT NULL REFERENCES cluster (id),
    name text NOT NULL
);

CREATE TABLE IF NOT EXISTS service_role (
    id uuid PRIMARY KEY,
    host_id uuid NOT NULL REFERENCES host (id),
    role text NOT NULL,
    replica_count integer,
    capacity_bytes bigint,
    queue_name text,
    memory_mb integer,
    vcores integer
);
