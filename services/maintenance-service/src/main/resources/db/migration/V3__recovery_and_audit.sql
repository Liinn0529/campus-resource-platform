ALTER TABLE maintenance.ticket_event DROP CONSTRAINT ticket_event_action_check;
ALTER TABLE maintenance.ticket_event ADD CONSTRAINT ticket_event_action_check CHECK(action IN
 ('REPORTED','ASSIGNED','START_REQUESTED','FINISHED','ACCEPT_REQUESTED','ACCEPTANCE_REJECTED','CANCELLED','REVIEWED','ESCALATED','SYNC_ATTEMPTED',
 'SYNC_RETRY_SCHEDULED','SYNC_FAILED','OCCUPANCY_CONFIRMED','RELEASE_CONFIRMED','RECOVERY_REQUESTED'));

CREATE TABLE maintenance.audit_outbox (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 source_event_id VARCHAR(96) NOT NULL UNIQUE,action VARCHAR(64) NOT NULL,
 entity_id BIGINT NOT NULL CHECK(entity_id>0),actor_user_id BIGINT CHECK(actor_user_id>0),
 occurred_at TIMESTAMPTZ NOT NULL,source_request_id UUID NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','DELIVERED','FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 3),next_attempt_at TIMESTAMPTZ,
 claim_token UUID,claimed_until TIMESTAMPTZ,error VARCHAR(64),
 CHECK((claim_token IS NULL)=(claimed_until IS NULL)),CHECK(status<>'PENDING' OR next_attempt_at IS NOT NULL)
);
CREATE INDEX audit_outbox_due ON maintenance.audit_outbox(next_attempt_at) WHERE status='PENDING';
