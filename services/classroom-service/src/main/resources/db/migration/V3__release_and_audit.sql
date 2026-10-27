ALTER TABLE classroom.classroom_request DROP CONSTRAINT classroom_request_status_check;
ALTER TABLE classroom.classroom_request ADD CONSTRAINT classroom_request_status_check CHECK(status IN
 ('SUBMITTED','APPROVING','APPROVED_PENDING_SYNC','APPROVED','RELEASING','COMPLETED','REJECTED','CANCELLED','AUTO_CANCELLED','SYNC_FAILED'));
ALTER TABLE classroom.classroom_request DROP CONSTRAINT classroom_sync_claim_valid;
ALTER TABLE classroom.classroom_request ADD CONSTRAINT classroom_sync_claim_valid CHECK
 ((sync_claim_token IS NULL AND sync_claimed_until IS NULL) OR
 (status IN ('APPROVED_PENDING_SYNC','SYNC_FAILED') AND sync_claim_token IS NOT NULL AND sync_claimed_until IS NOT NULL AND sync_attempts>0));
ALTER TABLE classroom.classroom_request DROP CONSTRAINT classroom_occupancy_receipt_valid;
ALTER TABLE classroom.classroom_request ADD CONSTRAINT classroom_occupancy_receipt_valid CHECK
 (status NOT IN ('APPROVED','RELEASING','COMPLETED') OR occupancy_id IS NOT NULL);
ALTER TABLE classroom.classroom_event ADD COLUMN note VARCHAR(500);
ALTER TABLE classroom.classroom_event DROP CONSTRAINT classroom_event_action_check;
ALTER TABLE classroom.classroom_event ADD CONSTRAINT classroom_event_action_check CHECK(action IN
 ('SUBMITTED','FIRST_APPROVED','FINAL_APPROVED','REJECTED','CANCELLED','AUTO_CANCELLED','SYNC_ATTEMPTED','SYNC_RETRY_SCHEDULED','SYNC_FAILED','OCCUPANCY_CONFIRMED',
 'CANCEL_REQUESTED','EXPIRY_RELEASE_REQUESTED','RELEASE_ATTEMPTED','RELEASE_RETRY_SCHEDULED','RELEASE_CONFIRMED','RECOVERY_REQUESTED'));
ALTER TABLE classroom.classroom_event DROP CONSTRAINT classroom_event_actor_valid;
ALTER TABLE classroom.classroom_event ADD CONSTRAINT classroom_event_actor_valid CHECK
 ((action IN ('SUBMITTED','FIRST_APPROVED','FINAL_APPROVED','REJECTED','CANCELLED','CANCEL_REQUESTED','RECOVERY_REQUESTED') AND actor_id IS NOT NULL)
 OR (action NOT IN ('SUBMITTED','FIRST_APPROVED','FINAL_APPROVED','REJECTED','CANCELLED','CANCEL_REQUESTED','RECOVERY_REQUESTED') AND actor_id IS NULL));
CREATE TABLE classroom.request_release (
 request_id BIGINT PRIMARY KEY REFERENCES classroom.classroom_request(id),
 reason VARCHAR(16) NOT NULL CHECK(reason IN ('CANCEL','EXPIRED')),
 status VARCHAR(16) NOT NULL CHECK(status IN ('PENDING','FAILED','CONFIRMED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 3),
 next_attempt_at TIMESTAMPTZ,claim_token UUID,claimed_until TIMESTAMPTZ,error VARCHAR(64),
 CHECK((claim_token IS NULL)=(claimed_until IS NULL)),
 CHECK(status<>'PENDING' OR next_attempt_at IS NOT NULL)
);
CREATE INDEX request_release_due ON classroom.request_release(next_attempt_at) WHERE status='PENDING';

CREATE TABLE classroom.audit_outbox (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 source_event_id VARCHAR(96) NOT NULL UNIQUE,action VARCHAR(64) NOT NULL,
 entity_id BIGINT NOT NULL CHECK(entity_id>0),actor_user_id BIGINT CHECK(actor_user_id>0),
 occurred_at TIMESTAMPTZ NOT NULL,source_request_id UUID NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','DELIVERED','FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts BETWEEN 0 AND 3),next_attempt_at TIMESTAMPTZ,
 claim_token UUID,claimed_until TIMESTAMPTZ,error VARCHAR(64),
 CHECK((claim_token IS NULL)=(claimed_until IS NULL)),CHECK(status<>'PENDING' OR next_attempt_at IS NOT NULL)
);
CREATE INDEX audit_outbox_due ON classroom.audit_outbox(next_attempt_at) WHERE status='PENDING';
