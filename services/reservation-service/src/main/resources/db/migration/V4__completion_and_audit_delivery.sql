ALTER TABLE reservation.reservation_event DROP CONSTRAINT reservation_event_action_check;
ALTER TABLE reservation.reservation_event ADD CONSTRAINT reservation_event_action_check
    CHECK (action IN ('CREATED', 'CANCELLED', 'REBOOKED', 'CHECKED_IN', 'NO_SHOW', 'COMPLETED'));
ALTER TABLE reservation.reservation_event DROP CONSTRAINT reservation_event_actor_valid;
ALTER TABLE reservation.reservation_event ADD CONSTRAINT reservation_event_actor_valid CHECK (
    (action IN ('NO_SHOW', 'COMPLETED') AND actor_user_id IS NULL)
    OR (action NOT IN ('NO_SHOW', 'COMPLETED') AND actor_user_id IS NOT NULL)
);

CREATE TABLE reservation.audit_delivery (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL UNIQUE REFERENCES reservation.reservation_event(id),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENDING', 'ACCEPTED', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 3),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claim_token UUID,
    claimed_until TIMESTAMPTZ,
    audit_id BIGINT CHECK (audit_id > 0),
    last_error_code VARCHAR(64),
    CHECK ((status = 'SENDING' AND claim_token IS NOT NULL AND claimed_until IS NOT NULL)
        OR (status <> 'SENDING' AND claim_token IS NULL AND claimed_until IS NULL)),
    CHECK ((status = 'ACCEPTED' AND audit_id IS NOT NULL)
        OR (status <> 'ACCEPTED' AND audit_id IS NULL))
);
CREATE INDEX audit_delivery_due ON reservation.audit_delivery(next_attempt_at, id)
    WHERE status IN ('PENDING', 'SENDING');

CREATE TABLE reservation.audit_attempt (
    delivery_id BIGINT NOT NULL REFERENCES reservation.audit_delivery(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number BETWEEN 1 AND 3),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    result_code VARCHAR(64),
    PRIMARY KEY (delivery_id, attempt_number)
);

-- The event and its delivery obligation commit or roll back together, including system events.
CREATE FUNCTION reservation.queue_audit_event() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO reservation.audit_delivery(event_id, next_attempt_at) VALUES (NEW.id, NEW.occurred_at);
    RETURN NEW;
END;
$$;
CREATE TRIGGER reservation_event_audit AFTER INSERT ON reservation.reservation_event
    FOR EACH ROW EXECUTE FUNCTION reservation.queue_audit_event();
