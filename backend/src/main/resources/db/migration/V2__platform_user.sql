-- Platform users for temporary header auth (vertical-slice ticket 01).
-- Roles: SENIOR = 高级角色, GENERAL = 一般角色 (CONTEXT / ADR-0041).

CREATE TABLE platform_user (
    id           TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    role         TEXT NOT NULL CHECK (role IN ('SENIOR', 'GENERAL')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO platform_user (id, display_name, role)
VALUES
    ('user-senior-demo', '演示主管', 'SENIOR'),
    ('user-general-demo', '演示运维', 'GENERAL');
