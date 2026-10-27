package cn.campus.resource.classroom;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface ClassroomMapper extends ReleaseMapper {
    record RequestRow(long id, long applicantId, long roomId, Instant startAt, Instant endAt, String purpose,
            String status, Instant submittedAt, Instant updatedAt, Instant approvalDueAt, int syncAttempts,
            Instant syncNextAttemptAt, UUID syncClaimToken, Instant syncClaimedUntil, String syncError, Long occupancyId) { }
    record Step(long id, long requestId, int stepNumber, String requiredRole, String status,
            Long actorId, Instant decidedAt, Instant dueAt) { }
    record Action(long id, long requestId, int stepNumber, long actorId, String action,
            String reason, Instant occurredAt) { }
    record Event(long id, long requestId, Long actorId, String action, UUID traceId, Instant occurredAt, String note) { }

    @Select(value = """
            INSERT INTO classroom.classroom_request
                (applicant_id, room_id, start_at, end_at, purpose, status, submitted_at, updated_at, approval_due_at)
            VALUES (#{applicantId}, #{roomId}, #{startAt}, #{endAt}, #{purpose}, 'SUBMITTED', #{now}, #{now}, #{dueAt})
            RETURNING *
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    RequestRow insert(@Param("applicantId") long applicantId, @Param("roomId") long roomId,
            @Param("startAt") Instant startAt, @Param("endAt") Instant endAt,
            @Param("purpose") String purpose, @Param("now") Instant now, @Param("dueAt") Instant dueAt);

    @Insert("""
            INSERT INTO classroom.approval_step (request_id, step_number, required_role, status, due_at)
            VALUES (#{id}, 1, 'ROOM_ADMIN', 'PENDING', #{dueAt}),
                   (#{id}, 2, 'DEPARTMENT_ADMIN', 'WAITING', NULL)
            """)
    int initialSteps(@Param("id") long id, @Param("dueAt") Instant dueAt);

    @Select("SELECT * FROM classroom.classroom_request WHERE id = #{id}")
    RequestRow request(long id);

    @Select("SELECT * FROM classroom.classroom_request WHERE id = #{id} FOR UPDATE")
    RequestRow lockRequest(long id);

    @Select("""
            SELECT * FROM classroom.classroom_request WHERE applicant_id = #{actorId}
            ORDER BY submitted_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<RequestRow> own(@Param("actorId") long actorId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            <script>
            SELECT r.* FROM classroom.classroom_request r
            JOIN classroom.approval_step s ON s.request_id = r.id AND s.status = 'PENDING'
            WHERE r.status IN ('SUBMITTED', 'APPROVING') AND r.approval_due_at &gt; #{now}
              AND r.applicant_id &lt;&gt; #{actorId}
              AND ((s.required_role = 'ROOM_ADMIN' AND #{roomAdmin})
                OR (s.required_role = 'DEPARTMENT_ADMIN' AND #{departmentAdmin}))
              AND r.room_id IN
              <foreach collection="roomIds" item="roomId" open="(" separator="," close=")">#{roomId}</foreach>
              AND NOT EXISTS (SELECT 1 FROM classroom.approval_action a
                  WHERE a.request_id = r.id AND a.actor_id = #{actorId} AND a.action = 'APPROVE')
            ORDER BY r.approval_due_at, r.id LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RequestRow> pending(@Param("actorId") long actorId, @Param("roomIds") Set<Long> roomIds,
            @Param("roomAdmin") boolean roomAdmin, @Param("departmentAdmin") boolean departmentAdmin,
            @Param("now") Instant now, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT * FROM classroom.approval_step WHERE request_id = #{id} ORDER BY step_number")
    List<Step> steps(long id);

    @Select("SELECT * FROM classroom.approval_step WHERE request_id = #{id} AND status = 'PENDING'")
    Step pendingStep(long id);

    @Select("SELECT * FROM classroom.approval_action WHERE request_id = #{id} ORDER BY id")
    List<Action> actions(long id);

    @Select("SELECT * FROM classroom.classroom_event WHERE request_id = #{id} ORDER BY id")
    List<Event> events(long id);

    @Select("SELECT EXISTS (SELECT 1 FROM classroom.approval_action WHERE request_id = #{id} AND actor_id = #{actorId} AND action = 'APPROVE')")
    boolean alreadyApproved(@Param("id") long id, @Param("actorId") long actorId);

    @Update("""
            UPDATE classroom.approval_step SET status = #{status}, actor_id = #{actorId}, decided_at = #{now}
            WHERE request_id = #{id} AND step_number = #{step} AND status = 'PENDING'
            """)
    int decideStep(@Param("id") long id, @Param("step") int step, @Param("status") String status,
            @Param("actorId") long actorId, @Param("now") Instant now);

    @Update("""
            UPDATE classroom.approval_step SET status = 'PENDING', due_at = #{dueAt}
            WHERE request_id = #{id} AND step_number = 2 AND status = 'WAITING'
            """)
    int activateDepartment(@Param("id") long id, @Param("dueAt") Instant dueAt);

    @Update("""
            UPDATE classroom.approval_step SET status = #{status}, decided_at = #{now}
            WHERE request_id = #{id} AND status IN ('WAITING', 'PENDING')
            """)
    int closeSteps(@Param("id") long id, @Param("status") String status, @Param("now") Instant now);

    @Update("""
            UPDATE classroom.classroom_request SET status = #{status}, approval_due_at = #{dueAt}, updated_at = #{now}
            WHERE id = #{id}
            """)
    int approvalState(@Param("id") long id, @Param("status") String status,
            @Param("dueAt") Instant dueAt, @Param("now") Instant now);

    @Update("""
            UPDATE classroom.classroom_request SET status = 'APPROVED_PENDING_SYNC', approval_due_at = NULL,
                sync_next_attempt_at = #{now}, updated_at = #{now}
            WHERE id = #{id}
            """)
    int queueSync(@Param("id") long id, @Param("now") Instant now);

    @Insert("""
            INSERT INTO classroom.approval_action (request_id, step_number, actor_id, action, reason, occurred_at)
            VALUES (#{id}, #{step}, #{actorId}, #{action}, #{reason}, #{now})
            """)
    int action(@Param("id") long id, @Param("step") int step, @Param("actorId") long actorId,
            @Param("action") String action, @Param("reason") String reason, @Param("now") Instant now);

    @Insert("""
            INSERT INTO classroom.classroom_event (request_id, actor_id, action, trace_id, occurred_at)
            VALUES (#{id}, #{actorId}, #{action}, #{traceId}, #{now})
            """)
    int event(@Param("id") long id, @Param("actorId") Long actorId, @Param("action") String action,
            @Param("traceId") UUID traceId, @Param("now") Instant now);

    @Select("""
            SELECT id FROM classroom.classroom_request
            WHERE status IN ('SUBMITTED', 'APPROVING') AND approval_due_at <= #{now}
            ORDER BY approval_due_at, id LIMIT 100
            """)
    List<Long> timedOut(Instant now);

    @Select("""
            SELECT id FROM classroom.classroom_request WHERE status = 'APPROVED_PENDING_SYNC'
              AND ((sync_claim_token IS NULL AND sync_next_attempt_at <= #{now}) OR sync_claimed_until <= #{now})
            ORDER BY sync_next_attempt_at, id LIMIT 100
            """)
    List<Long> syncDue(Instant now);

    @Update("""
            UPDATE classroom.classroom_request SET sync_attempts = sync_attempts + 1,
                sync_claim_token = #{token}, sync_claimed_until = #{until}, sync_error = NULL, updated_at = #{now}
            WHERE id = #{id}
            """)
    int claimSync(@Param("id") long id, @Param("token") UUID token,
            @Param("until") Instant until, @Param("now") Instant now);

    @Update("""
            UPDATE classroom.classroom_request SET status = #{status}, sync_error = #{error},
                sync_next_attempt_at = #{nextAt}, sync_claim_token = NULL, sync_claimed_until = NULL, updated_at = #{now}
            WHERE id = #{id}
            """)
    int syncFailure(@Param("id") long id, @Param("status") String status, @Param("error") String error,
            @Param("nextAt") Instant nextAt, @Param("now") Instant now);

    @Update("""
            UPDATE classroom.classroom_request SET status = 'APPROVED', occupancy_id = #{occupancyId},
                sync_error = NULL, sync_next_attempt_at = NULL, sync_claim_token = NULL,
                sync_claimed_until = NULL, updated_at = #{now}
            WHERE id = #{id}
            """)
    int synchronizedState(@Param("id") long id, @Param("occupancyId") long occupancyId, @Param("now") Instant now);

    @Update("UPDATE classroom.classroom_request SET sync_claim_token=#{token},sync_claimed_until=#{until},updated_at=#{now} WHERE id=#{id}")
    int claimRecovery(@Param("id") long id,@Param("token") UUID token,@Param("until") Instant until,@Param("now") Instant now);
    @Update("UPDATE classroom.classroom_request SET status=#{status},sync_error=NULL,sync_next_attempt_at=NULL,sync_claim_token=NULL,sync_claimed_until=NULL,updated_at=#{now} WHERE id=#{id}")
    int releasedState(@Param("id") long id,@Param("status") String status,@Param("now") Instant now);
    @Insert("INSERT INTO classroom.classroom_event(request_id,actor_id,action,trace_id,occurred_at,note) VALUES(#{id},#{actor},'RECOVERY_REQUESTED',#{trace},#{now},#{reason})")
    int recoveryEvent(@Param("id") long id,@Param("actor") long actor,@Param("reason") String reason,@Param("trace") UUID trace,@Param("now") Instant now);

    @Insert("INSERT INTO classroom.audit_outbox(source_event_id,action,entity_id,actor_user_id,occurred_at,source_request_id,next_attempt_at) "
            + "SELECT 'classroom-event:'||e.id,e.action,e.request_id,e.actor_id,e.occurred_at,e.trace_id,e.occurred_at "
            + "FROM classroom.classroom_event e WHERE e.request_id=#{id} ORDER BY e.id DESC LIMIT 1 ON CONFLICT(source_event_id) DO NOTHING")
    int enqueueAudit(long id);
}
