-- Ticket 01: 未绑定观测候选 upsert by field entity (source host + runtime id).
-- Do not edit V4. Partial unique so rows without runtime_id remain insertable.

CREATE UNIQUE INDEX unbound_observation_candidate_host_runtime_uq
    ON unbound_observation_candidate (source_host_id, runtime_id)
    WHERE runtime_id IS NOT NULL;
