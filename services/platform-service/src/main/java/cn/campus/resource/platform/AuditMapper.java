package cn.campus.resource.platform;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface AuditMapper {
    record Event(long id, Long actorUserId, String sourceService, String sourceEventId, String action,
            String targetType, String targetId, String requestId, Instant occurredAt) { }
    record ExternalEvent(long id, Long actorUserId, String action, String targetType, String targetId,
            String requestId, Instant occurredAt) { }

    @Insert("""
            INSERT INTO platform.audit_log
                (actor_user_id, source_service, action, target_type, target_id, request_id, occurred_at)
            VALUES (#{actorId}, 'platform-service', #{action}, #{targetType}, #{targetId},
                #{requestId}, #{occurredAt})
            """)
    int insert(@Param("actorId") long actorId, @Param("action") String action,
            @Param("targetType") String targetType, @Param("targetId") String targetId,
            @Param("requestId") UUID requestId, @Param("occurredAt") Instant occurredAt);

    @Insert("""
            INSERT INTO platform.audit_log
                (actor_user_id, source_service, source_event_id, action, target_type, target_id, request_id, occurred_at)
            VALUES (#{actorId}, #{sourceService}, #{sourceEventId}, #{action}, #{targetType}, #{targetId},
                #{requestId}, #{occurredAt})
            ON CONFLICT (source_service, source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING
            """)
    int insertExternal(@Param("actorId") Long actorId, @Param("sourceService") String sourceService,
            @Param("sourceEventId") String sourceEventId, @Param("action") String action,
            @Param("targetType") String targetType, @Param("targetId") String targetId,
            @Param("requestId") UUID requestId, @Param("occurredAt") Instant occurredAt);

    @Select("""
            SELECT id, actor_user_id AS "actorUserId", action, target_type AS "targetType",
                target_id AS "targetId", request_id::text AS "requestId", occurred_at AS "occurredAt"
            FROM platform.audit_log WHERE source_service = #{sourceService} AND source_event_id = #{sourceEventId}
            """)
    ExternalEvent external(@Param("sourceService") String sourceService,
            @Param("sourceEventId") String sourceEventId);

    @Select("""
            SELECT id, actor_user_id AS "actorUserId", source_service AS "sourceService",
                source_event_id AS "sourceEventId", action,
                target_type AS "targetType", target_id AS "targetId", request_id::text AS "requestId",
                occurred_at AS "occurredAt"
            FROM platform.audit_log WHERE (#{targetType}::varchar IS NULL OR target_type = #{targetType})
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Event> events(@Param("targetType") String targetType, @Param("limit") int limit,
            @Param("offset") int offset);
}
