CREATE TABLE platform.notification (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES platform."user" (id),
    source_module VARCHAR(16) NOT NULL CHECK (source_module = 'M1'),
    source_ref_id VARCHAR(64) NOT NULL CHECK (source_ref_id ~ '^[A-Za-z0-9][A-Za-z0-9:_-]{0,63}$'),
    reservation_id BIGINT NOT NULL CHECK (reservation_id > 0),
    room_id BIGINT NOT NULL CHECK (room_id > 0),
    start_at TIMESTAMPTZ NOT NULL,
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('CHECKIN_FIRST', 'CHECKIN_SECOND')),
    due_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT notification_source_unique UNIQUE (source_module, source_ref_id),
    CONSTRAINT notification_window_valid CHECK (
        (kind = 'CHECKIN_FIRST' AND due_at = start_at - INTERVAL '15 minutes'
            AND expires_at = start_at + INTERVAL '10 minutes')
        OR (kind = 'CHECKIN_SECOND' AND due_at = start_at + INTERVAL '10 minutes'
            AND expires_at = start_at + INTERVAL '15 minutes')
    )
);

CREATE INDEX notification_user_id ON platform.notification (user_id, id DESC);
