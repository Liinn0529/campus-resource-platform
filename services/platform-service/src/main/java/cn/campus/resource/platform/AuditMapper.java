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
    record Event(long id, Long actorUserId, String sourceService, String action, String targetType,
            String targetId, String requestId, Instant occurredAt) { }

    @Insert("""
            INSERT INTO platform.audit_log
                (actor_user_id, source_service, action, target_type, target_id, request_id, occurred_at)
            VALUES (#{actorId}, 'platform-service', #{action}, #{targetType}, #{targetId},
                #{requestId}, #{occurredAt})
            """)
    int insert(@Param("actorId") long actorId, @Param("action") String action,
            @Param("targetType") String targetType, @Param("targetId") String targetId,
            @Param("requestId") UUID requestId, @Param("occurredAt") Instant occurredAt);

    @Select("""
            SELECT id, actor_user_id AS "actorUserId", source_service AS "sourceService", action,
                target_type AS "targetType", target_id AS "targetId", request_id::text AS "requestId",
                occurred_at AS "occurredAt"
            FROM platform.audit_log WHERE (#{targetType}::varchar IS NULL OR target_type = #{targetType})
            ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<Event> events(@Param("targetType") String targetType, @Param("limit") int limit,
            @Param("offset") int offset);
}
