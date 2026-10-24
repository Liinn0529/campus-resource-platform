package cn.campus.resource.platform;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditService {
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
}
