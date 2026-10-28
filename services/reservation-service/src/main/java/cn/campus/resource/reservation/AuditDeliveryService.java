package cn.campus.resource.reservation;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class AuditDeliveryService {
    private record Dispatch(long id, int attempt, UUID token, AuditDeliveryMapper.Event event) { }
    private final AuditDeliveryMapper mapper;
    private final PlatformClient platform;
    private final Clock clock;
    private final TransactionTemplate transactions;

    AuditDeliveryService(AuditDeliveryMapper mapper, PlatformClient platform, Clock clock, PlatformTransactionManager manager) {
        this.mapper = mapper; this.platform = platform; this.clock = clock;
        transactions = new TransactionTemplate(manager); transactions.setTimeout(10);
    }

    void dispatchDue() {
        for (long id : mapper.due(clock.instant())) {
            Dispatch work = transactions.execute(tx -> {
                var row = mapper.lock(id);
                Instant now = clock.instant();
                if (row == null || !(row.status().equals("PENDING") && !now.isBefore(row.nextAttemptAt())
                        || row.status().equals("SENDING") && !now.isBefore(row.claimedUntil()))) { return null; }
                if (row.status().equals("SENDING")) {
                    mapper.finishAttempt(id, row.attemptCount(), now, "DELIVERY_ACK_UNKNOWN");
                }
                if (row.attemptCount() >= 3) {
                    mapper.finish(id, row.claimToken(), "FAILED", null, "DELIVERY_ACK_UNKNOWN", now);
                    return null;
                }
                UUID token = UUID.randomUUID();
                mapper.claim(id, token, now.plusSeconds(30));
                mapper.attempt(id, row.attemptCount() + 1, now);
                return new Dispatch(id, row.attemptCount() + 1, token, mapper.event(row.eventId()));
            });
            if (work == null) { continue; }
            Long receipt = null;
            PlatformClient.NotificationFailure error = null;
            try { receipt = platform.audit(work.event()); }
            catch (PlatformClient.NotificationFailure failure) { error = failure; }
            Long auditId = receipt;
            var failure = error;
            transactions.executeWithoutResult(tx -> {
                var row = mapper.lock(work.id());
                if (!row.status().equals("SENDING") || !work.token().equals(row.claimToken())) { return; }
                Instant now = clock.instant();
                String status = failure == null ? "ACCEPTED"
                        : failure.retryable() && work.attempt() < 3 ? "PENDING" : "FAILED";
                mapper.finish(work.id(), work.token(), status, auditId, failure == null ? null : failure.code(),
                        now.plusSeconds(30L * work.attempt()));
                mapper.finishAttempt(work.id(), work.attempt(), now, failure == null ? "ACCEPTED" : failure.code());
            });
        }
    }
}
