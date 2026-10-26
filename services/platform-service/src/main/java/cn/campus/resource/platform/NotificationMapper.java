package cn.campus.resource.platform;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface NotificationMapper {
    record Notification(long id, long userId, String sourceModule, String sourceRefId,
            long reservationId, long roomId, Instant startAt, String kind,
            Instant dueAt, Instant expiresAt, Instant createdAt) { }

    @Select("""
            SELECT id, user_id, source_module, source_ref_id, reservation_id, room_id,
                start_at, kind, due_at, expires_at, created_at
            FROM platform.notification WHERE source_module = 'M1' AND source_ref_id = #{sourceRefId}
            """)
    Notification bySource(String sourceRefId);

    @Select("SELECT enabled FROM platform.\"user\" WHERE id = #{userId} FOR SHARE")
    Boolean lockRecipient(long userId);

    @Select(value = """
            INSERT INTO platform.notification
                (user_id, source_module, source_ref_id, reservation_id, room_id,
                 start_at, kind, due_at, expires_at, created_at)
            VALUES (#{userId}, 'M1', #{sourceRefId}, #{reservationId}, #{roomId},
                 #{startAt}, #{kind}, #{dueAt}, #{expiresAt}, #{createdAt})
            ON CONFLICT (source_module, source_ref_id) DO NOTHING
            RETURNING id, user_id, source_module, source_ref_id, reservation_id, room_id,
                start_at, kind, due_at, expires_at, created_at
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Notification insert(@Param("sourceRefId") String sourceRefId, @Param("userId") long userId,
            @Param("reservationId") long reservationId, @Param("roomId") long roomId,
            @Param("startAt") Instant startAt, @Param("kind") String kind,
            @Param("dueAt") Instant dueAt, @Param("expiresAt") Instant expiresAt,
            @Param("createdAt") Instant createdAt);

    @Select("""
            SELECT id, user_id, source_module, source_ref_id, reservation_id, room_id,
                start_at, kind, due_at, expires_at, created_at
            FROM platform.notification WHERE user_id = #{userId}
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Notification> own(@Param("userId") long userId, @Param("limit") int limit,
            @Param("offset") int offset);

    @Insert("""
            INSERT INTO platform.audit_log
                (actor_user_id, source_service, action, target_type, target_id, request_id, occurred_at)
            VALUES (NULL, 'reservation-service', 'NOTIFICATION_ACCEPTED', 'NOTIFICATION', #{targetId},
                #{requestId}, #{occurredAt})
            """)
    int audit(@Param("targetId") String targetId, @Param("requestId") UUID requestId,
            @Param("occurredAt") Instant occurredAt);
}
