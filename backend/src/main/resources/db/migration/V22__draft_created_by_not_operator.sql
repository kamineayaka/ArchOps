-- ADR-0046 ticket 01: 诊断选支 of 改理想 does not record an operator on the draft.
-- Forward-only: do not edit V13.

ALTER TABLE curated_draft
    DROP CONSTRAINT curated_draft_created_by_fkey;

ALTER TABLE curated_draft
    ALTER COLUMN created_by DROP NOT NULL;
