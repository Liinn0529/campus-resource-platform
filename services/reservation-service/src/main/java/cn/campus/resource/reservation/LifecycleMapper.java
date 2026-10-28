package cn.campus.resource.reservation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface LifecycleMapper {
    record Candidate(long id, long roomId) { }
    record Violation(long id, long reservationId, String reason, Instant occurredAt) { }
    record Reminder(long id, long reservationId, String kind, Instant dueAt, Instant expiresAt,
            String status, int attemptCount, Instant nextAttemptAt, UUID claimToken, Instant claimedUntil,
            Long notificationId, Instant acceptedAt, String lastErrorCode) { }

    @Select("""
            SELECT id, room_id FROM reservation.seat_reservation r
            WHERE status = 'CREATED' AND (
                start_at + INTERVAL '15 minutes' <= #{now}
                OR (start_at - INTERVAL '15 minutes' <= #{now} AND #{now} < start_at + INTERVAL '10 minutes'
                    AND NOT EXISTS (SELECT 1 FROM reservation.reminder m
                        WHERE m.reservation_id = r.id AND m.kind = 'CHECKIN_FIRST'))
                OR (start_at + INTERVAL '10 minutes' <= #{now} AND #{now} < start_at + INTERVAL '15 minutes'
                    AND NOT EXISTS (SELECT 1 FROM reservation.reminder m
                        WHERE m.reservation_id = r.id AND m.kind = 'CHECKIN_SECOND'))
            ) ORDER BY start_at, id LIMIT 100
            """)
    List<Candidate> due(Instant now);

    @Select("SELECT id, room_id FROM reservation.seat_reservation WHERE status='CHECKED_IN' AND end_at <= #{now} ORDER BY end_at, id LIMIT 100")
    List<Candidate> completedDue(Instant now);

    @Update("UPDATE reservation.seat_reservation SET status='COMPLETED' WHERE id=#{id} AND status='CHECKED_IN'")
    int complete(long id);

    @Insert("INSERT INTO reservation.reservation_event(reservation_id, actor_user_id, action, request_id, occurred_at) VALUES (#{id}, NULL, 'COMPLETED', #{requestId}, #{now})")
    int completedEvent(@Param("id") long id, @Param("requestId") UUID requestId, @Param("now") Instant now);

    @Select("""
            SELECT id, user_id, room_id, seat_id, start_at, end_at, status, created_at, cancelled_at, rebook_of
            FROM reservation.seat_reservation WHERE id = #{id} FOR UPDATE
            """)
    ReservationMapper.Booking lockBooking(long id);

    @Select("SELECT count(*) FROM reservation.checkin WHERE reservation_id = #{id} AND user_id = #{userId}")
    int checkinCount(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT count(*) FROM reservation.seat_occupancy
            WHERE source_module = 'M1' AND source_type = 'RESERVATION' AND source_ref_id = #{sourceRef}
              AND room_id = #{roomId} AND seat_id = #{seatId} AND start_at = #{startAt} AND end_at = #{endAt}
              AND status = 'ACTIVE' AND released_at IS NULL
            """)
    int matchingActiveLedger(@Param("sourceRef") String sourceRef, @Param("roomId") long roomId,
            @Param("seatId") long seatId, @Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

    @Update("UPDATE reservation.seat_reservation SET status = 'CHECKED_IN' WHERE id = #{id} AND status = 'CREATED'")
    int markCheckedIn(long id);

    @Insert("""
            INSERT INTO reservation.checkin (reservation_id, user_id, checked_in_at)
            VALUES (#{id}, #{userId}, #{now})
            """)
    int insertCheckin(@Param("id") long id, @Param("userId") long userId, @Param("now") Instant now);

    @Update("UPDATE reservation.seat_reservation SET status = 'NO_SHOW' WHERE id = #{id} AND status = 'CREATED'")
    int markNoShow(long id);

    @Insert("""
            INSERT INTO reservation.violation (reservation_id, user_id, reason, occurred_at)
            VALUES (#{id}, #{userId}, 'NO_SHOW', #{now})
            """)
    int insertViolation(@Param("id") long id, @Param("userId") long userId, @Param("now") Instant now);

    @Insert("""
            INSERT INTO reservation.reservation_event
                (reservation_id, actor_user_id, action, request_id, occurred_at)
            VALUES (#{id}, NULL, 'NO_SHOW', #{requestId}, #{now})
            """)
    int noShowEvent(@Param("id") long id, @Param("requestId") UUID requestId, @Param("now") Instant now);

    @Select("""
            SELECT id, reservation_id, reason, occurred_at FROM reservation.violation
            WHERE user_id = #{userId} ORDER BY occurred_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Violation> violations(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset);

    @Insert("""
            INSERT INTO reservation.reminder (reservation_id, kind, due_at, expires_at, status, next_attempt_at)
            VALUES (#{id}, #{kind}, #{dueAt}, #{expiresAt}, 'PENDING', #{dueAt})
            ON CONFLICT (reservation_id, kind) DO NOTHING
            """)
    int queueReminder(@Param("id") long id, @Param("kind") String kind,
            @Param("dueAt") Instant dueAt, @Param("expiresAt") Instant expiresAt);

    @Update("""
            UPDATE reservation.reminder SET status = 'EXPIRED', claim_token = NULL, claimed_until = NULL,
                last_error_code = #{reason}
            WHERE reservation_id = #{id} AND (status = 'PENDING'
                OR (status = 'SENDING' AND claimed_until <= #{now}))
            """)
    int suppressPending(@Param("id") long id, @Param("now") Instant now, @Param("reason") String reason);

    @Select("""
            SELECT m.id, r.room_id FROM reservation.reminder m
            JOIN reservation.seat_reservation r ON r.id = m.reservation_id
            WHERE (m.status = 'PENDING' AND m.next_attempt_at <= #{now})
               OR (m.status = 'SENDING' AND m.claimed_until <= #{now})
            ORDER BY m.next_attempt_at, m.id LIMIT 100
            """)
    List<Candidate> dispatchable(Instant now);

    @Select("""
            SELECT id, reservation_id, kind, due_at, expires_at, status, attempt_count, next_attempt_at,
                claim_token, claimed_until, notification_id, accepted_at, last_error_code
            FROM reservation.reminder WHERE id = #{id}
            """)
    Reminder reminder(long id);

    @Select("""
            SELECT id, reservation_id, kind, due_at, expires_at, status, attempt_count, next_attempt_at,
                claim_token, claimed_until, notification_id, accepted_at, last_error_code
            FROM reservation.reminder WHERE id = #{id} FOR UPDATE
            """)
    Reminder lockReminder(long id);

    @Update("""
            UPDATE reservation.reminder SET status = 'SENDING', attempt_count = attempt_count + 1,
                claim_token = #{token}, claimed_until = #{until}, last_error_code = NULL
            WHERE id = #{id}
            """)
    int claim(@Param("id") long id, @Param("token") UUID token, @Param("until") Instant until);

    @Update("""
            UPDATE reservation.reminder SET status = #{status}, claim_token = NULL, claimed_until = NULL,
                last_error_code = #{code}
            WHERE id = #{id}
            """)
    int finishUnclaimed(@Param("id") long id, @Param("status") String status, @Param("code") String code);

    @Update("""
            UPDATE reservation.reminder SET status = 'ACCEPTED', notification_id = #{notificationId},
                accepted_at = #{now}, claim_token = NULL, claimed_until = NULL, last_error_code = NULL
            WHERE id = #{id} AND status = 'SENDING' AND claim_token = #{token}
            """)
    int accepted(@Param("id") long id, @Param("token") UUID token,
            @Param("notificationId") long notificationId, @Param("now") Instant now);

    @Update("""
            UPDATE reservation.reminder SET status = #{status}, next_attempt_at = #{nextAttemptAt},
                claim_token = NULL, claimed_until = NULL, last_error_code = #{code}
            WHERE id = #{id} AND status = 'SENDING' AND claim_token = #{token}
            """)
    int deliveryFailure(@Param("id") long id, @Param("token") UUID token,
            @Param("status") String status, @Param("nextAttemptAt") Instant nextAttemptAt, @Param("code") String code);
}
