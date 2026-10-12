-- Probe 改理想 stores a pending draft item. Opening it does not write 策展.

ALTER TABLE curated_draft_item DROP CONSTRAINT curated_draft_item_kind_check;

ALTER TABLE curated_draft_item
    ADD CONSTRAINT curated_draft_item_kind_check
        CHECK (kind IN (
            'RUNS_ON_TARGET_CHANGE',
            'CREATE_CONTAINER_FROM_UNBOUND',
            'BIND_UNBOUND_TO_EXISTING',
            'CURATED_RUNS_ON_INSERT',
            'PROBE_CURATED_VALUE'
        ));
