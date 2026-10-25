ALTER TABLE platform."user"
    ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0 CHECK (auth_version >= 0);

CREATE TABLE platform.user_room_scope (
    user_id BIGINT NOT NULL REFERENCES platform."user" (id),
    room_id BIGINT NOT NULL REFERENCES platform.room (id),
    PRIMARY KEY (user_id, room_id)
);

CREATE TABLE platform.system_setting (
    key VARCHAR(64) PRIMARY KEY,
    value INTEGER NOT NULL CHECK (value > 0)
);

CREATE TABLE platform.room_checkin_code (
    room_id BIGINT NOT NULL REFERENCES platform.room (id),
    date DATE NOT NULL,
    code VARCHAR(8) NOT NULL CHECK (code ~ '^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$'),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (room_id, date)
);

CREATE TABLE platform.audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id BIGINT REFERENCES platform."user" (id),
    source_service VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    request_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX audit_log_occurred_id ON platform.audit_log (occurred_at DESC, id DESC);
REVOKE UPDATE, DELETE, TRUNCATE ON TABLE platform.audit_log FROM platform_app;
