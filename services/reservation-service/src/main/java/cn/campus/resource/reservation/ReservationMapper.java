package cn.campus.resource.reservation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface ReservationMapper {
    record Booking(long id, long userId, long roomId, long seatId, Instant startAt, Instant endAt,
            String status, Instant createdAt, Instant cancelledAt, Long rebookOf) { }
    record Occupancy(Long seatId, Instant startAt, Instant endAt) { }

    @Insert("INSERT INTO reservation.room_gate (room_id) VALUES (#{roomId}) ON CONFLICT (room_id) DO NOTHING")
    int ensureGate(long roomId);

    @Select("SELECT room_id FROM reservation.room_gate WHERE room_id = #{roomId} FOR UPDATE")
    Long lockGate(long roomId);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM reservation.seat_occupancy
                WHERE room_id = #{roomId} AND status = 'ACTIVE'
                  AND (seat_id = #{seatId} OR seat_id IS NULL)
                  AND start_at < #{endAt} AND (end_at IS NULL OR end_at > #{startAt})
            )
            """)
    boolean conflict(@Param("roomId") long roomId, @Param("seatId") long seatId,
            @Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

    @Select("""
            SELECT seat_id, start_at, end_at FROM reservation.seat_occupancy
            WHERE room_id = #{roomId} AND status = 'ACTIVE'
              AND start_at < #{endAt} AND (end_at IS NULL OR end_at > #{startAt})
            """)
    List<Occupancy> conflicts(@Param("roomId") long roomId,
            @Param("startAt") Instant startAt, @Param("endAt") Instant endAt);

    @Select(value = """
            INSERT INTO reservation.seat_reservation
                (user_id, room_id, seat_id, start_at, end_at, status, created_at, rebook_of)
            VALUES (#{userId}, #{roomId}, #{seatId}, #{startAt}, #{endAt}, 'CREATED', #{createdAt}, #{rebookOf})
            RETURNING id, user_id, room_id, seat_id, start_at, end_at, status, created_at, cancelled_at, rebook_of
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Booking insert(@Param("userId") long userId, @Param("roomId") long roomId,
            @Param("seatId") long seatId, @Param("startAt") Instant startAt, @Param("endAt") Instant endAt,
            @Param("createdAt") Instant createdAt, @Param("rebookOf") Long rebookOf);

    @Insert("""
            INSERT INTO reservation.seat_occupancy
                (room_id, seat_id, start_at, end_at, source_module, source_type, source_ref_id,
                 status, created_at)
            VALUES (#{roomId}, #{seatId}, #{startAt}, #{endAt}, 'M1', 'RESERVATION', #{sourceRef},
                    'ACTIVE', #{createdAt})
            """)
    int occupy(@Param("roomId") long roomId, @Param("seatId") long seatId,
            @Param("startAt") Instant startAt, @Param("endAt") Instant endAt,
            @Param("sourceRef") String sourceRef, @Param("createdAt") Instant createdAt);

    @Select("""
            SELECT id, user_id, room_id, seat_id, start_at, end_at, status, created_at, cancelled_at, rebook_of
            FROM reservation.seat_reservation WHERE id = #{id} AND user_id = #{userId}
            """)
    Booking owned(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT id, user_id, room_id, seat_id, start_at, end_at, status, created_at, cancelled_at, rebook_of
            FROM reservation.seat_reservation WHERE id = #{id} AND user_id = #{userId} FOR UPDATE
            """)
    Booking lockOwned(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT id, user_id, room_id, seat_id, start_at, end_at, status, created_at, cancelled_at, rebook_of
            FROM reservation.seat_reservation WHERE user_id = #{userId}
              AND ((#{view} = 'current' AND status IN ('CREATED', 'CHECKED_IN') AND end_at > #{now})
                OR (#{view} = 'history' AND (status IN ('CANCELLED', 'NO_SHOW', 'COMPLETED') OR end_at <= #{now})))
            ORDER BY start_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Booking> list(@Param("userId") long userId, @Param("view") String view,
            @Param("now") Instant now, @Param("limit") int limit, @Param("offset") int offset);

    @Update("""
            UPDATE reservation.seat_reservation SET status = 'CANCELLED', cancelled_at = #{cancelledAt}
            WHERE id = #{id} AND status = 'CREATED'
            """)
    int cancel(@Param("id") long id, @Param("cancelledAt") Instant cancelledAt);

    @Update("""
            UPDATE reservation.seat_occupancy SET status = 'RELEASED', released_at = #{releasedAt}
            WHERE room_id = #{roomId} AND seat_id = #{seatId}
              AND source_module = 'M1' AND source_type = 'RESERVATION' AND source_ref_id = #{sourceRef}
              AND status = 'ACTIVE'
            """)
    int release(@Param("roomId") long roomId, @Param("seatId") long seatId,
            @Param("sourceRef") String sourceRef, @Param("releasedAt") Instant releasedAt);

    @Insert("""
            INSERT INTO reservation.reservation_event
                (reservation_id, actor_user_id, action, request_id, occurred_at)
            VALUES (#{reservationId}, #{actorId}, #{action}, #{requestId}, #{occurredAt})
            """)
    int event(@Param("reservationId") long reservationId, @Param("actorId") long actorId,
            @Param("action") String action, @Param("requestId") UUID requestId,
            @Param("occurredAt") Instant occurredAt);
}
