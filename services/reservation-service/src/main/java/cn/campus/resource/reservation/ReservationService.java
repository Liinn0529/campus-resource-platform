package cn.campus.resource.reservation;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class ReservationService {
    record SeatAvailability(long id, String code, boolean hasPowerOutlet, boolean available) { }
    record Availability(long roomId, Instant startAt, Instant endAt, List<SeatAvailability> seats) { }

    private final ReservationMapper reservations;
    private final LifecycleMapper lifecycle;
    private final PlatformClient platform;
    private final AccessPolicy access;
    private final TimeRules time;
    private final Clock clock;
    private final TransactionTemplate transactions;

    ReservationService(ReservationMapper reservations, LifecycleMapper lifecycle, PlatformClient platform, AccessPolicy access,
            TimeRules time, Clock clock, PlatformTransactionManager transactionManager) {
        this.reservations = reservations;
        this.lifecycle = lifecycle;
        this.platform = platform;
        this.access = access;
        this.time = time;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setTimeout(10);
    }

    List<PlatformClient.Room> rooms(Long buildingId, int limit, int offset) {
        access.require("space:view");
        return platform.rooms(buildingId, limit, offset);
    }

    List<PlatformClient.Seat> seats(long roomId, Boolean hasPowerOutlet, int limit, int offset) {
        access.require("space:view");
        availableRoom(roomId);
        return platform.seats(roomId, hasPowerOutlet, limit, offset);
    }

    Availability availability(long roomId, OffsetDateTime startAt, OffsetDateTime endAt,
            Boolean hasPowerOutlet, int limit, int offset) {
        access.require("space:view");
        TimeRules.Interval interval = time.interval(startAt, endAt);
        availableRoom(roomId);
        List<PlatformClient.Seat> seats = platform.seats(roomId, hasPowerOutlet, limit, offset);
        List<ReservationMapper.Occupancy> occupied = reservations.conflicts(roomId,
                interval.startAt(), interval.endAt());
        return new Availability(roomId, interval.startAt(), interval.endAt(), seats.stream()
                .map(seat -> new SeatAvailability(seat.id(), seat.code(), seat.hasPowerOutlet(),
                        occupied.stream().noneMatch(row -> row.seatId() == null || row.seatId() == seat.id())))
                .toList());
    }

    ReservationMapper.Booking create(long roomId, long seatId, OffsetDateTime startAt, OffsetDateTime endAt) {
        access.require("reservation:create");
        return createFor(access.actor().id(), roomId, seatId, startAt, endAt, null);
    }

    List<ReservationMapper.Booking> list(String view, int limit, int offset) {
        access.require("reservation:view:self");
        return reservations.list(access.actor().id(), view, clock.instant(), limit, offset);
    }

    ReservationMapper.Booking detail(long id) {
        access.require("reservation:view:self");
        return owned(id, access.actor().id());
    }

    ReservationMapper.Booking cancel(long id) {
        access.require("reservation:create");
        long userId = access.actor().id();
        ReservationMapper.Booking previous = owned(id, userId);
        return transactions.execute(status -> {
            lockRoom(previous.roomId());
            ReservationMapper.Booking current = reservations.lockOwned(id, userId);
            if (current == null) { throw missing(); }
            if (current.status().equals("CANCELLED")) { return current; }
            Instant now = clock.instant();
            if (!current.status().equals("CREATED") || !now.isBefore(current.startAt())) {
                throw new ApiFailure(HttpStatus.CONFLICT, "RESERVATION_CANNOT_CANCEL",
                        "仅可在预约开始前取消本人尚未开始的预约");
            }
            if (reservations.cancel(id, now) != 1
                    || reservations.release(current.roomId(), current.seatId(), Long.toString(id), now) != 1) {
                throw new IllegalStateException("Reservation and occupancy state are inconsistent");
            }
            reservations.event(id, userId, "CANCELLED", UUID.fromString(RequestIds.current()), now);
            lifecycle.suppressPending(id, now, "RESERVATION_CANCELLED");
            return reservations.owned(id, userId);
        });
    }

    ReservationMapper.Booking rebook(long id, OffsetDateTime startAt, OffsetDateTime endAt) {
        access.require("reservation:create");
        long userId = access.actor().id();
        ReservationMapper.Booking previous = owned(id, userId);
        if (!Set.of("CANCELLED", "NO_SHOW", "COMPLETED").contains(previous.status())
                && previous.endAt().isAfter(clock.instant())) {
            throw new ApiFailure(HttpStatus.CONFLICT, "RESERVATION_NOT_HISTORY", "仅可重新预约本人的历史记录");
        }
        return createFor(userId, previous.roomId(), previous.seatId(), startAt, endAt, previous.id());
    }

    private ReservationMapper.Booking createFor(long userId, long roomId, long seatId,
            OffsetDateTime startAt, OffsetDateTime endAt, Long rebookOf) {
        TimeRules.Interval interval = time.interval(startAt, endAt);
        availableRoom(roomId);
        PlatformClient.Seat seat = platform.seat(roomId, seatId);
        if (!seat.enabled() || seat.roomId() != roomId || seat.id() != seatId) {
            throw unavailable();
        }
        int maxHours = platform.settings().maxReservationHours();
        time.bookable(interval, maxHours, clock.instant());
        // Metadata is fetched before entering the M1 transaction; no network call holds the room lock.
        return transactions.execute(status -> {
            lockRoom(roomId);
            Instant now = clock.instant();
            time.bookable(interval, maxHours, now);
            if (reservations.conflict(roomId, seatId, interval.startAt(), interval.endAt())) {
                throw new ApiFailure(HttpStatus.CONFLICT, "RESERVATION_TIME_CONFLICT", "所选时段已不可预约");
            }
            ReservationMapper.Booking created = reservations.insert(userId, roomId, seatId,
                    interval.startAt(), interval.endAt(), now, rebookOf);
            if (reservations.occupy(roomId, seatId, interval.startAt(), interval.endAt(),
                    Long.toString(created.id()), now) != 1) {
                throw new IllegalStateException("Reservation occupancy could not be recorded");
            }
            reservations.event(created.id(), userId, rebookOf == null ? "CREATED" : "REBOOKED",
                    UUID.fromString(RequestIds.current()), now);
            return created;
        });
    }

    private void lockRoom(long roomId) {
        // The insert and lock share a transaction, including the very first concurrent booking for a room.
        reservations.ensureGate(roomId);
        if (reservations.lockGate(roomId) == null) {
            throw new IllegalStateException("Required room gate is missing");
        }
    }

    private PlatformClient.Room availableRoom(long roomId) {
        PlatformClient.Room room = platform.room(roomId);
        if (!room.enabled() || room.id() != roomId) { throw unavailable(); }
        return room;
    }

    private ReservationMapper.Booking owned(long id, long userId) {
        ReservationMapper.Booking value = reservations.owned(id, userId);
        if (value == null) { throw missing(); }
        return value;
    }

    private ApiFailure missing() {
        return new ApiFailure(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", "预约记录不存在");
    }

    private ApiFailure unavailable() {
        return new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "RESOURCE_UNAVAILABLE", "房间或座位当前不可预约");
    }
}
