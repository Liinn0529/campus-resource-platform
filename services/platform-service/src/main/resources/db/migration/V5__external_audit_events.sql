ALTER TABLE platform.audit_log
    ADD COLUMN source_event_id VARCHAR(64);

ALTER TABLE platform.audit_log
    ADD CONSTRAINT audit_log_source_event_id_format
        CHECK (source_event_id IS NULL OR source_event_id ~ '^[A-Za-z0-9][A-Za-z0-9:_-]{0,63}$');

CREATE UNIQUE INDEX audit_log_source_event_unique
    ON platform.audit_log (source_service, source_event_id)
    WHERE source_event_id IS NOT NULL;

COMMENT ON COLUMN platform.audit_log.source_event_id IS
    'Stable producer event key for idempotent cross-service audit receipt; null for D-native records.';
