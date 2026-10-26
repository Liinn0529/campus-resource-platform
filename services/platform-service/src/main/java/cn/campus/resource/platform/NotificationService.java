package cn.campus.resource.platform;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class NotificationService {
    record Acceptance(NotificationMapper.Notification notification, boolean created) { }

    private final NotificationMapper notifications;
    private final AccessPolicy access;
    private final Clock clock;

    NotificationService(NotificationMapper notifications, AccessPolicy access, Clock clock) {
        this.notifications = notifications;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    Acceptance accept(NotificationController.NotificationWrite input) {
        Instant startAt = input.startAt().toInstant();
        Instant dueAt = input.dueAt().toInstant();
        Instant expiresAt = input.expiresAt().toInstant();
        boolean first = input.kind().equals("CHECKIN_FIRST");
        // PostgreSQL timestamps preserve microseconds; reject finer input so retries compare exactly.
        if (startAt.getNano() % 1_000 != 0 || dueAt.getNano() % 1_000 != 0 || expiresAt.getNano() % 1_000 != 0
                || !dueAt.equals(startAt.plus(Duration.ofMinutes(first ? -15 : 10)))
                || !expiresAt.equals(startAt.plus(Duration.ofMinutes(first ? 10 : 15)))) {
            throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "NOTIFICATION_INVALID_WINDOW",
                    "提醒时段不符合预约规则");
        }
        var existing = notifications.bySource(input.sourceRefId());
        if (existing != null) { return existing(input, existing); }
        if (!Boolean.TRUE.equals(notifications.lockRecipient(input.userId()))) {
            throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "NOTIFICATION_RECIPIENT_UNAVAILABLE",
                    "提醒接收人不可用");
        }
        Instant now = clock.instant();
        var created = notifications.insert(input.sourceRefId(), input.userId(), input.reservationId(),
                input.roomId(), startAt, input.kind(), dueAt, expiresAt, now);
        if (created == null) {
            // A concurrent request can win the unique source key after the first read.
            return existing(input, notifications.bySource(input.sourceRefId()));
        }
        notifications.audit(Long.toString(created.id()), UUID.fromString(RequestIds.current()), now);
        return new Acceptance(created, true);
    }

    List<NotificationMapper.Notification> own(int limit, int offset) {
        access.require("reservation:view:self");
        return notifications.own(access.actor().id(), limit, offset);
    }

    private Acceptance existing(NotificationController.NotificationWrite input,
            NotificationMapper.Notification existing) {
        if (existing == null) { throw new IllegalStateException("Notification source was not retained"); }
        if (existing.userId() != input.userId() || existing.reservationId() != input.reservationId()
                || existing.roomId() != input.roomId() || !existing.startAt().equals(input.startAt().toInstant())
                || !existing.kind().equals(input.kind()) || !existing.dueAt().equals(input.dueAt().toInstant())
                || !existing.expiresAt().equals(input.expiresAt().toInstant())) {
            throw new ApiFailure(HttpStatus.CONFLICT, "NOTIFICATION_SOURCE_CONFLICT", "提醒来源与已有内容冲突");
        }
        return new Acceptance(existing, false);
    }
}
