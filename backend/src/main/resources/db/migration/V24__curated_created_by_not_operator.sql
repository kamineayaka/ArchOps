-- Ticket 03: accepting an unbound draft item can create curated rows without an operator.
-- Null stays allowed. The user foreign keys stay so a real user id can still be stored.

ALTER TABLE curated_object
    ALTER COLUMN created_by DROP NOT NULL;

ALTER TABLE curated_fact
    ALTER COLUMN created_by DROP NOT NULL;
