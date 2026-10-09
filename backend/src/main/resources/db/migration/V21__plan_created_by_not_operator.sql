-- ADR-0046 ticket 01: 诊断选支 does not record an operator on the operation plan.
-- created_by stays nullable so a later ticket can stop treating it as a domain fact.
-- Forward-only: do not edit V8.

ALTER TABLE operation_plan
    DROP CONSTRAINT operation_plan_created_by_fkey;

ALTER TABLE operation_plan
    ALTER COLUMN created_by DROP NOT NULL;
