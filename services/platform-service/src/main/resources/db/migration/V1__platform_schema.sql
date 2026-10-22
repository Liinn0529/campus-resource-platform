CREATE TABLE platform."user" (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_password_hash_bcrypt CHECK (
        password_hash ~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$'
    )
);

CREATE TABLE platform.role (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE platform.permission (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE platform.user_role (
    user_id BIGINT NOT NULL REFERENCES platform."user" (id),
    role_id BIGINT NOT NULL REFERENCES platform.role (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE platform.role_permission (
    role_id BIGINT NOT NULL REFERENCES platform.role (id),
    permission_id BIGINT NOT NULL REFERENCES platform.permission (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE platform.building (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE platform.room (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    building_id BIGINT NOT NULL REFERENCES platform.building (id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT room_building_code_unique UNIQUE (building_id, code),
    CONSTRAINT room_type_valid CHECK (type IN ('STUDY_ROOM', 'CLASSROOM'))
);

CREATE TABLE platform.seat (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES platform.room (id),
    code VARCHAR(64) NOT NULL,
    has_power_outlet BOOLEAN NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT seat_room_code_unique UNIQUE (room_id, code)
);

REVOKE ALL ON TABLE platform.flyway_schema_history FROM platform_app;
