CREATE TABLE maintenance.repair_ticket (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id BIGINT NOT NULL CHECK (reporter_id > 0),
    room_id BIGINT NOT NULL CHECK (room_id > 0),
    description VARCHAR(500) NOT NULL CHECK (length(btrim(description)) > 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('REPORTED','ASSIGNED','BLOCKING_ROOM','IN_PROGRESS','WAITING_ACCEPTANCE','COMPLETED','CANCELLED','SYNC_FAILED')),
    assignee_id BIGINT CHECK (assignee_id > 0 AND assignee_id <> reporter_id),
    reported_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ,
    repair_cycle INTEGER NOT NULL DEFAULT 0 CHECK (repair_cycle >= 0),
    escalated BOOLEAN NOT NULL DEFAULT FALSE,
    block_start_at TIMESTAMPTZ,
    occupancy_id BIGINT CHECK (occupancy_id > 0),
    sync_operation VARCHAR(8) CHECK (sync_operation IN ('BLOCK','RELEASE')),
    sync_attempts INTEGER NOT NULL DEFAULT 0 CHECK (sync_attempts BETWEEN 0 AND 3),
    sync_next_attempt_at TIMESTAMPTZ,
    sync_claim_token UUID,
    sync_claimed_until TIMESTAMPTZ,
    sync_error VARCHAR(64),
    CHECK ((sync_claim_token IS NULL) = (sync_claimed_until IS NULL)),
    CHECK (status IN ('REPORTED','CANCELLED') OR (assignee_id IS NOT NULL AND due_at IS NOT NULL)),
    CHECK (status NOT IN ('IN_PROGRESS','WAITING_ACCEPTANCE','COMPLETED') OR occupancy_id IS NOT NULL),
    CHECK (status <> 'BLOCKING_ROOM' OR (sync_operation='BLOCK' AND block_start_at IS NOT NULL)),
    CHECK (sync_operation IS DISTINCT FROM 'RELEASE' OR occupancy_id IS NOT NULL)
);
CREATE INDEX repair_ticket_owner ON maintenance.repair_ticket(reporter_id,id DESC);
CREATE INDEX repair_ticket_assignee ON maintenance.repair_ticket(assignee_id,id DESC);
CREATE INDEX repair_ticket_due ON maintenance.repair_ticket(due_at) WHERE NOT escalated;
CREATE INDEX repair_ticket_sync ON maintenance.repair_ticket(sync_next_attempt_at) WHERE sync_operation IS NOT NULL;

CREATE TABLE maintenance.ticket_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES maintenance.repair_ticket(id),
    actor_user_id BIGINT CHECK (actor_user_id > 0),
    action VARCHAR(32) NOT NULL CHECK (action IN ('REPORTED','ASSIGNED','START_REQUESTED','FINISHED','ACCEPT_REQUESTED','ACCEPTANCE_REJECTED','CANCELLED','REVIEWED','ESCALATED','SYNC_ATTEMPTED','SYNC_RETRY_SCHEDULED','SYNC_FAILED','OCCUPANCY_CONFIRMED','RELEASE_CONFIRMED')),
    repair_cycle INTEGER NOT NULL CHECK (repair_cycle >= 0),
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CHECK ((actor_user_id IS NULL) = (action IN ('ESCALATED','SYNC_ATTEMPTED','SYNC_RETRY_SCHEDULED','SYNC_FAILED','OCCUPANCY_CONFIRMED','RELEASE_CONFIRMED')))
);
CREATE INDEX ticket_event_ticket ON maintenance.ticket_event(ticket_id,id);
CREATE UNIQUE INDEX ticket_escalation_once ON maintenance.ticket_event(ticket_id,repair_cycle) WHERE action='ESCALATED';
CREATE TABLE maintenance.ticket_review (
    ticket_id BIGINT PRIMARY KEY REFERENCES maintenance.repair_ticket(id),
    reviewer_id BIGINT NOT NULL CHECK (reviewer_id > 0),
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL
);
