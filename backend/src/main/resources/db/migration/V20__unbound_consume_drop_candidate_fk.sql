-- Ticket 04: label-match consume deletes unbound_observation_candidate rows.
-- OPEN/VOIDED 未绑定草案 keep candidate_id as an audit pointer; V17's FK would
-- block consume. Forward-only: do not edit V1–V19.

ALTER TABLE curated_draft
    DROP CONSTRAINT curated_draft_candidate_fk;
