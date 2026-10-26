CREATE TABLE classroom.classroom_request (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    applicant_id BIGINT NOT NULL CHECK (applicant_id > 0),
    room_id BIGINT NOT NULL CHECK (room_id > 0),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    purpose VARCHAR(500) NOT NULL CHECK (length(btrim(purpose)) > 0),
    status VARCHAR(32) NOT NULL CHECK (status IN (
        'SUBMITTED', 'APPROVING', 'APPROVED_PENDING_SYNC', 'APPROVED',
        'REJECTED', 'CANCELLED', 'AUTO_CANCELLED', 'SYNC_FAILED'
    )),
    submitted_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    approval_due_at TIMESTAMPTZ,
    sync_attempts INTEGER NOT NULL DEFAULT 0 CHECK (sync_attempts BETWEEN 0 AND 3),
    sync_next_attempt_at TIMESTAMPTZ,
    sync_claim_token UUID,
    sync_claimed_until TIMESTAMPTZ,
    sync_error VARCHAR(64),
    occupancy_id BIGINT CHECK (occupancy_id > 0),
    CONSTRAINT classroom_interval_valid CHECK (end_at > start_at),
    CONSTRAINT classroom_approval_deadline_valid CHECK (
        (status IN ('SUBMITTED', 'APPROVING') AND approval_due_at IS NOT NULL)
        OR (status NOT IN ('SUBMITTED', 'APPROVING') AND approval_due_at IS NULL)
    ),
    CONSTRAINT classroom_sync_claim_valid CHECK (
        (sync_claim_token IS NULL AND sync_claimed_until IS NULL)
        OR (status = 'APPROVED_PENDING_SYNC' AND sync_claim_token IS NOT NULL
            AND sync_claimed_until IS NOT NULL AND sync_attempts > 0)
    ),
    CONSTRAINT classroom_occupancy_receipt_valid CHECK (
        (status = 'APPROVED' AND occupancy_id IS NOT NULL)
        OR (status <> 'APPROVED' AND occupancy_id IS NULL)
    )
);

CREATE INDEX classroom_request_owner_time ON classroom.classroom_request (applicant_id, submitted_at DESC, id DESC);
CREATE INDEX classroom_request_approval_due ON classroom.classroom_request (approval_due_at, id)
    WHERE status IN ('SUBMITTED', 'APPROVING');
CREATE INDEX classroom_request_sync_due ON classroom.classroom_request (sync_next_attempt_at, id)
    WHERE status = 'APPROVED_PENDING_SYNC';

CREATE TABLE classroom.approval_step (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES classroom.classroom_request (id),
    step_number INTEGER NOT NULL CHECK (step_number IN (1, 2)),
    required_role VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('WAITING', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    actor_id BIGINT CHECK (actor_id > 0),
    decided_at TIMESTAMPTZ,
    due_at TIMESTAMPTZ,
    CONSTRAINT approval_step_unique UNIQUE (request_id, step_number),
    CONSTRAINT approval_step_role_valid CHECK (
        (step_number = 1 AND required_role = 'ROOM_ADMIN')
        OR (step_number = 2 AND required_role = 'DEPARTMENT_ADMIN')
    ),
    CONSTRAINT approval_step_actor_valid CHECK (
        (status IN ('APPROVED', 'REJECTED') AND actor_id IS NOT NULL AND decided_at IS NOT NULL)
        OR (status IN ('WAITING', 'PENDING') AND actor_id IS NULL AND decided_at IS NULL)
        OR (status IN ('CANCELLED', 'EXPIRED') AND actor_id IS NULL AND decided_at IS NOT NULL)
    ),
    CONSTRAINT approval_step_pending_deadline CHECK (status <> 'PENDING' OR due_at IS NOT NULL)
);

CREATE TABLE classroom.approval_action (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES classroom.classroom_request (id),
    step_number INTEGER NOT NULL CHECK (step_number IN (1, 2)),
    actor_id BIGINT NOT NULL CHECK (actor_id > 0),
    action VARCHAR(16) NOT NULL CHECK (action IN ('APPROVE', 'REJECT')),
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT approval_action_once UNIQUE (request_id, step_number)
);

CREATE TABLE classroom.classroom_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES classroom.classroom_request (id),
    actor_id BIGINT CHECK (actor_id > 0),
    action VARCHAR(32) NOT NULL CHECK (action IN (
        'SUBMITTED', 'FIRST_APPROVED', 'FINAL_APPROVED', 'REJECTED', 'CANCELLED',
        'AUTO_CANCELLED', 'SYNC_ATTEMPTED', 'SYNC_RETRY_SCHEDULED', 'SYNC_FAILED', 'OCCUPANCY_CONFIRMED'
    )),
    trace_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT classroom_event_actor_valid CHECK (
        (action IN ('SUBMITTED', 'FIRST_APPROVED', 'FINAL_APPROVED', 'REJECTED', 'CANCELLED') AND actor_id IS NOT NULL)
        OR (action IN ('AUTO_CANCELLED', 'SYNC_ATTEMPTED', 'SYNC_RETRY_SCHEDULED', 'SYNC_FAILED', 'OCCUPANCY_CONFIRMED') AND actor_id IS NULL)
    )
);

CREATE INDEX classroom_event_request_id ON classroom.classroom_event (request_id, id);
