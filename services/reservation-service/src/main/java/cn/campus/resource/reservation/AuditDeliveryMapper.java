package cn.campus.resource.reservation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
interface AuditDeliveryMapper {
    record Delivery(long id, long eventId, String status, int attemptCount, Instant nextAttemptAt,
            UUID claimToken, Instant claimedUntil) { }
    record Event(long id, long reservationId, Long actorUserId, String action, UUID requestId, Instant occurredAt) { }

    @Select("""
            SELECT id FROM reservation.audit_delivery
            WHERE (status='PENDING' AND next_attempt_at<=#{now}) OR (status='SENDING' AND claimed_until<=#{now})
            ORDER BY next_attempt_at, id LIMIT 100
            """)
    List<Long> due(Instant now);

    @Select("SELECT id,event_id,status,attempt_count,next_attempt_at,claim_token,claimed_until FROM reservation.audit_delivery WHERE id=#{id} FOR UPDATE")
    Delivery lock(long id);

    @Select("SELECT id,reservation_id,actor_user_id,action,request_id,occurred_at FROM reservation.reservation_event WHERE id=#{id}")
    Event event(long id);

    @Update("UPDATE reservation.audit_delivery SET status='SENDING',attempt_count=attempt_count+1,claim_token=#{token},claimed_until=#{until} WHERE id=#{id}")
    int claim(@Param("id") long id, @Param("token") UUID token, @Param("until") Instant until);

    @Insert("INSERT INTO reservation.audit_attempt(delivery_id,attempt_number,started_at) VALUES(#{id},#{attempt},#{now})")
    int attempt(@Param("id") long id, @Param("attempt") int attempt, @Param("now") Instant now);

    @Update("UPDATE reservation.audit_attempt SET finished_at=#{now},result_code=#{code} WHERE delivery_id=#{id} AND attempt_number=#{attempt} AND finished_at IS NULL")
    int finishAttempt(@Param("id") long id, @Param("attempt") int attempt,
            @Param("now") Instant now, @Param("code") String code);

    @Update("""
            UPDATE reservation.audit_delivery SET status=#{status},audit_id=#{auditId},last_error_code=#{code},
                next_attempt_at=#{next},claim_token=NULL,claimed_until=NULL
            WHERE id=#{id} AND status='SENDING' AND claim_token=#{token}
            """)
    int finish(@Param("id") long id, @Param("token") UUID token, @Param("status") String status,
            @Param("auditId") Long auditId, @Param("code") String code, @Param("next") Instant next);
}
