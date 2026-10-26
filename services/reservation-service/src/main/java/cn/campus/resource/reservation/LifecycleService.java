package cn.campus.resource.reservation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class LifecycleService {
    private static final Duration FIRST_LEAD = Duration.ofMinutes(15);
    private static final Duration SECOND_DELAY = Duration.ofMinutes(10);
    private static final Duration DEADLINE_DELAY = Duration.ofMinutes(15);
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);

    private record Dispatch(LifecycleMapper.Reminder reminder, ReservationMapper.Booking booking, UUID token) { }

    private final LifecycleMapper lifecycle;
    private final ReservationMapper reservations;
    private final PlatformClient platform;
    private final AccessPolicy access;
    private final Clock clock;
    private final TransactionTemplate transactions;

    LifecycleService(LifecycleMapper lifecycle, ReservationMapper reservations, PlatformClient platform,
            AccessPolicy access, Clock clock, PlatformTransactionManager transactionManager) {
        this.lifecycle = lifecycle;
        this.reservations = reservations;
        this.platform = platform;
        this.access = access;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setTimeout(10);
    }

    ReservationMapper.Booking checkin(long id, String code) {
        access.require("reservation:create");
        long userId = access.actor().id();
        ReservationMapper.Booking previous = reservations.owned(id, userId);
        if (previous == null) { throw missing(); }
        if (!previous.status().equals("CHECKED_IN")) {
            requireCheckinWindow(previous, clock.instant());
            // No network request holds the room lock; the deadline is checked again after the wait.
            if (!platform.verifyCheckinCode(previous.roomId(), code)) {
                throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "CHECKIN_CODE_INVALID", "签到码无效");
            }
        }
        return transactions.execute(transaction -> {
            lockRoom(previous.roomId());
            ReservationMapper.Booking current = reservations.lockOwned(id, userId);
            if (current == null) { throw missing(); }
            if (current.status().equals("CHECKED_IN")) {
                if (lifecycle.checkinCount(id, userId) != 1) { throw inconsistent(); }
                return current;
            }
            Instant now = clock.instant();
            requireCheckinWindow(current, now);
            requireActiveLedger(current);
            if (lifecycle.markCheckedIn(id) != 1 || lifecycle.insertCheckin(id, userId, now) != 1) {
                throw inconsistent();
            }
            lifecycle.suppressPending(id, now, "RESERVATION_CHECKED_IN");
            reservations.event(id, userId, "CHECKED_IN", UUID.fromString(RequestIds.current()), now);
            return reservations.owned(id, userId);
        });
    }

    List<LifecycleMapper.Violation> violations(int limit, int offset) {
        access.require("reservation:view:self");
        return lifecycle.violations(access.actor().id(), limit, offset);
    }

    public void runOnce() {
        scanDue();
        dispatchDue();
    }

    void scanDue() {
        RuntimeException firstFailure = null;
        for (LifecycleMapper.Candidate candidate : lifecycle.due(clock.instant())) {
            try {
                process(candidate);
            } catch (RuntimeException failure) {
                // One damaged ledger must not prevent other rooms from reaching their deadlines.
                if (firstFailure == null) { firstFailure = failure; }
            }
        }
        if (firstFailure != null) { throw firstFailure; }
    }

    void dispatchDue() {
        RuntimeException firstFailure = null;
        for (LifecycleMapper.Candidate candidate : lifecycle.dispatchable(clock.instant())) {
            try {
                dispatch(candidate);
            } catch (RuntimeException failure) {
                if (firstFailure == null) { firstFailure = failure; }
            }
        }
        if (firstFailure != null) { throw firstFailure; }
    }

    private void process(LifecycleMapper.Candidate candidate) {
        transactions.executeWithoutResult(transaction -> {
            lockRoom(candidate.roomId());
            ReservationMapper.Booking booking = lifecycle.lockBooking(candidate.id());
            if (booking == null || !booking.status().equals("CREATED")) { return; }
            Instant now = clock.instant();
            Instant first = booking.startAt().minus(FIRST_LEAD);
            Instant second = booking.startAt().plus(SECOND_DELAY);
            Instant deadline = booking.startAt().plus(DEADLINE_DELAY);
            if (!now.isBefore(deadline)) {
                requireActiveLedger(booking);
                if (lifecycle.markNoShow(booking.id()) != 1
                        || reservations.release(booking.roomId(), booking.seatId(), Long.toString(booking.id()), now) != 1
                        || lifecycle.insertViolation(booking.id(), booking.userId(), now) != 1) {
                    throw inconsistent();
                }
                lifecycle.suppressPending(booking.id(), now, "RESERVATION_NO_SHOW");
                lifecycle.noShowEvent(booking.id(), UUID.randomUUID(), now);
                return;
            }
            if (!now.isBefore(second)) {
                lifecycle.queueReminder(booking.id(), "CHECKIN_SECOND", second, deadline);
            } else if (!now.isBefore(first)) {
                lifecycle.queueReminder(booking.id(), "CHECKIN_FIRST", first, second);
            }
        });
    }

    private void dispatch(LifecycleMapper.Candidate candidate) {
        LifecycleMapper.Reminder snapshot = lifecycle.reminder(candidate.id());
        if (snapshot == null) { return; }
        Dispatch claimed = transactions.execute(transaction -> {
            lockRoom(candidate.roomId());
            ReservationMapper.Booking booking = lifecycle.lockBooking(snapshot.reservationId());
            LifecycleMapper.Reminder reminder = lifecycle.lockReminder(candidate.id());
            Instant now = clock.instant();
            boolean ready = reminder != null && ((reminder.status().equals("PENDING")
                    && !now.isBefore(reminder.nextAttemptAt())) || (reminder.status().equals("SENDING")
                    && !now.isBefore(reminder.claimedUntil())));
            if (!ready) { return null; }
            if (booking == null || !booking.status().equals("CREATED") || !now.isBefore(reminder.expiresAt())) {
                lifecycle.finishUnclaimed(reminder.id(), "EXPIRED", "REMINDER_NO_LONGER_APPLICABLE");
                return null;
            }
            if (now.isBefore(reminder.dueAt())) { return null; }
            if (reminder.attemptCount() >= 3) {
                lifecycle.finishUnclaimed(reminder.id(), "FAILED", "DELIVERY_ACK_UNKNOWN");
                return null;
            }
            UUID token = UUID.randomUUID();
            lifecycle.claim(reminder.id(), token, now.plus(CLAIM_LEASE));
            return new Dispatch(lifecycle.reminder(reminder.id()), booking, token);
        });
        if (claimed == null) { return; }
        var reminder = claimed.reminder();
        var booking = claimed.booking();
        try {
            long notification = platform.notifyReminder(reminder.id(), booking.userId(), booking.id(),
                    booking.roomId(), booking.startAt(), reminder.kind(), reminder.dueAt(), reminder.expiresAt());
            lifecycle.accepted(reminder.id(), claimed.token(), notification, clock.instant());
        } catch (PlatformClient.NotificationFailure failure) {
            Instant now = clock.instant();
            String next = !now.isBefore(reminder.expiresAt()) ? "EXPIRED"
                    : failure.retryable() && reminder.attemptCount() < 3 ? "PENDING" : "FAILED";
            lifecycle.deliveryFailure(reminder.id(), claimed.token(), next,
                    now.plusSeconds(30L * reminder.attemptCount()), failure.code());
        }
    }

    private void requireCheckinWindow(ReservationMapper.Booking booking, Instant now) {
        if (!booking.status().equals("CREATED")) {
            throw new ApiFailure(HttpStatus.CONFLICT, "CHECKIN_NOT_ALLOWED", "预约当前不可签到");
        }
        if (now.isBefore(booking.startAt().minus(FIRST_LEAD))) {
            throw new ApiFailure(HttpStatus.CONFLICT, "CHECKIN_NOT_OPEN", "签到尚未开放");
        }
        if (!now.isBefore(booking.startAt().plus(DEADLINE_DELAY))) {
            throw new ApiFailure(HttpStatus.CONFLICT, "CHECKIN_CLOSED", "签到已截止");
        }
    }

    private void requireActiveLedger(ReservationMapper.Booking booking) {
        if (lifecycle.matchingActiveLedger(Long.toString(booking.id()), booking.roomId(), booking.seatId(),
                booking.startAt(), booking.endAt()) != 1) {
            throw inconsistent();
        }
    }

    private void lockRoom(long roomId) {
        reservations.ensureGate(roomId);
        if (reservations.lockGate(roomId) == null) { throw inconsistent(); }
    }

    private ApiFailure missing() {
        return new ApiFailure(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "预约记录不存在");
    }

    private IllegalStateException inconsistent() {
        return new IllegalStateException("Reservation lifecycle and ledger are inconsistent");
    }
}
