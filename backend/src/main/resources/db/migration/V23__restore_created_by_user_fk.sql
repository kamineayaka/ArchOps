-- Ticket 01 only stops writing an operator. Null is enough for that.
-- A user reference may still be written by later paths (未绑定草案). Restore the
-- foreign keys dropped in V21 / V22. Null remains allowed.

ALTER TABLE operation_plan
    ADD CONSTRAINT operation_plan_created_by_fkey
        FOREIGN KEY (created_by) REFERENCES platform_user (id);

ALTER TABLE curated_draft
    ADD CONSTRAINT curated_draft_created_by_fkey
        FOREIGN KEY (created_by) REFERENCES platform_user (id);
