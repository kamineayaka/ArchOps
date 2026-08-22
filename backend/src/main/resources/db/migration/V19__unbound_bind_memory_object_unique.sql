-- Ticket 08: one 策展 object is the identity of at most one field entity.
-- Forward-only: do not edit V1–V18.

-- Deliberately no de-duplication first. A database written by the pre-fix code can hold two
-- memories for one object, and picking a survivor would be choosing a 并入 no human confirmed;
-- let this index fail loudly so the operator deletes the wrong row (or re-binds) by hand.
CREATE UNIQUE INDEX unbound_bind_memory_object_uq
    ON unbound_bind_memory (curated_object_id);

-- The unique index above covers the lookup the plain index served.
DROP INDEX IF EXISTS unbound_bind_memory_object_idx;
