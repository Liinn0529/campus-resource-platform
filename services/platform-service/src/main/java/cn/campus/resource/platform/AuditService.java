package cn.campus.resource.platform;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditService {
    record Receipt(long id, boolean created) { }
    private final AuditMapper mapper;
    private final AccessPolicy access;
    private final Clock clock;

    AuditService(AuditMapper mapper, AccessPolicy access, Clock clock) {
        this.mapper = mapper;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String targetType, String targetId) {
        mapper.insert(access.actor().id(), action, targetType, targetId,
                UUID.fromString(RequestIds.current()), clock.instant());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String targetType, long targetId) {
        record(action, targetType, Long.toString(targetId));
    }

    @Transactional(readOnly = true)
    List<AuditMapper.Event> events(String targetType, int limit, int offset) {
        access.requireGlobal("audit:view");
        return mapper.events(targetType, limit, offset);
    }

    @Transactional
    Receipt receive(String sourceService, String sourceEventId, String action, String targetType,
            String targetId, Long actorUserId, Instant occurredAt, UUID sourceRequestId) {
        Instant storedAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
        int inserted = mapper.insertExternal(actorUserId, sourceService, sourceEventId, action,
                targetType, targetId, sourceRequestId, storedAt);
        AuditMapper.ExternalEvent existing = mapper.external(sourceService, sourceEventId);
        if (existing == null) {
            throw new IllegalStateException("Audit event insert returned no row");
        }
        boolean matches = existing.action().equals(action) && existing.targetType().equals(targetType)
                && existing.targetId().equals(targetId) && Objects.equals(existing.actorUserId(), actorUserId)
                && existing.requestId().equals(sourceRequestId.toString()) && existing.occurredAt().equals(storedAt);
        if (!matches) {
            throw new ApiFailure(HttpStatus.CONFLICT,
                    "AUDIT_EVENT_SOURCE_CONFLICT", "来源事件ID已用于不同内容");
        }
        return new Receipt(existing.id(), inserted == 1);
    }
}
