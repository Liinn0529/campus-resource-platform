CREATE TABLE reservation.checkin (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT NOT NULL UNIQUE REFERENCES reservation.seat_reservation (id),
    user_id BIGINT NOT NULL CHECK (user_id > 0),
    checked_in_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE reservation.violation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT NOT NULL UNIQUE REFERENCES reservation.seat_reservation (id),
    user_id BIGINT NOT NULL CHECK (user_id > 0),
    reason VARCHAR(32) NOT NULL CHECK (reason = 'NO_SHOW'),
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX violation_user_time ON reservation.violation (user_id, occurred_at DESC, id DESC);

CREATE TABLE reservation.reminder (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservation.seat_reservation (id),
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('CHECKIN_FIRST', 'CHECKIN_SECOND')),
    due_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'SENDING', 'ACCEPTED', 'EXPIRED', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 3),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claim_token UUID,
    claimed_until TIMESTAMPTZ,
    notification_id BIGINT CHECK (notification_id > 0),
    accepted_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    CONSTRAINT reminder_kind_unique UNIQUE (reservation_id, kind),
    CONSTRAINT reminder_window_valid CHECK (expires_at > due_at),
    CONSTRAINT reminder_claim_valid CHECK (
        (status = 'SENDING' AND claim_token IS NOT NULL AND claimed_until IS NOT NULL AND attempt_count > 0)
        OR (status <> 'SENDING' AND claim_token IS NULL AND claimed_until IS NULL)
    ),
    CONSTRAINT reminder_acceptance_valid CHECK (
        (status = 'ACCEPTED' AND notification_id IS NOT NULL AND accepted_at IS NOT NULL)
        OR (status <> 'ACCEPTED' AND notification_id IS NULL AND accepted_at IS NULL)
    )
);

CREATE INDEX reminder_dispatch_due ON reservation.reminder (next_attempt_at, id)
    WHERE status IN ('PENDING', 'SENDING');

ALTER TABLE reservation.reservation_event ALTER COLUMN actor_user_id DROP NOT NULL;
ALTER TABLE reservation.reservation_event DROP CONSTRAINT reservation_event_action_check;
ALTER TABLE reservation.reservation_event ADD CONSTRAINT reservation_event_action_check
    CHECK (action IN ('CREATED', 'CANCELLED', 'REBOOKED', 'CHECKED_IN', 'NO_SHOW'));
ALTER TABLE reservation.reservation_event ADD CONSTRAINT reservation_event_actor_valid CHECK (
    (action = 'NO_SHOW' AND actor_user_id IS NULL)
    OR (action <> 'NO_SHOW' AND actor_user_id IS NOT NULL)
);
