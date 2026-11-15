CREATE UNIQUE INDEX alert_one_current_per_host_role
    ON alert (host_id, role)
    WHERE current;
