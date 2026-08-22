-- Ticket 08: one 策展 object is the identity of at most one field entity.
-- Forward-only: do not edit V1–V18.

CREATE UNIQUE INDEX unbound_bind_memory_object_uq
    ON unbound_bind_memory (curated_object_id);

-- The unique index above covers the lookup the plain index served.
DROP INDEX unbound_bind_memory_object_idx;
