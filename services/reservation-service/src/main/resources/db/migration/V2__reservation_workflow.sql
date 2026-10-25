CREATE TABLE reservation.room_gate (
    room_id BIGINT PRIMARY KEY CHECK (room_id > 0)
);

CREATE TABLE reservation.seat_reservation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL CHECK (user_id > 0),
    room_id BIGINT NOT NULL CHECK (room_id > 0),
    seat_id BIGINT NOT NULL CHECK (seat_id > 0),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'CREATED', 'CANCELLED', 'CHECKED_IN', 'NO_SHOW', 'COMPLETED'
    )),
    created_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    rebook_of BIGINT REFERENCES reservation.seat_reservation (id),
    CONSTRAINT reservation_interval_valid CHECK (end_at > start_at),
    CONSTRAINT reservation_cancellation_valid CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL)
    )
);

CREATE INDEX seat_reservation_user_time ON reservation.seat_reservation (user_id, start_at DESC, id DESC);

CREATE TABLE reservation.seat_occupancy (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id BIGINT NOT NULL CHECK (room_id > 0),
    seat_id BIGINT CHECK (seat_id > 0),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ,
    source_module VARCHAR(16) NOT NULL CHECK (source_module IN ('M1', 'M2', 'M3')),
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN (
        'RESERVATION', 'CLASSROOM_BOOKING', 'MAINTENANCE'
    )),
    source_ref_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED')),
    created_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    CONSTRAINT occupancy_source_unique UNIQUE (source_module, source_ref_id, source_type),
    CONSTRAINT occupancy_interval_valid CHECK (end_at IS NULL OR end_at > start_at),
    CONSTRAINT occupancy_release_valid CHECK (
        (status = 'ACTIVE' AND released_at IS NULL)
        OR (status = 'RELEASED' AND released_at IS NOT NULL)
    )
);

CREATE INDEX seat_occupancy_active_room_start ON reservation.seat_occupancy (room_id, start_at)
    WHERE status = 'ACTIVE';

CREATE TABLE reservation.reservation_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservation.seat_reservation (id),
    actor_user_id BIGINT NOT NULL CHECK (actor_user_id > 0),
    action VARCHAR(32) NOT NULL CHECK (action IN ('CREATED', 'CANCELLED', 'REBOOKED')),
    request_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX reservation_event_reservation ON reservation.reservation_event (reservation_id, id);
