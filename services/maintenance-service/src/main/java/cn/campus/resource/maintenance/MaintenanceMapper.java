package cn.campus.resource.maintenance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;

@Mapper
interface MaintenanceMapper {
    record Ticket(long id, long reporterId, long roomId, String description, String status, Long assigneeId,
            Instant reportedAt, Instant updatedAt, Instant dueAt, int repairCycle, boolean escalated,
            Instant blockStartAt, Long occupancyId, String syncOperation, int syncAttempts,
            Instant syncNextAttemptAt, UUID syncClaimToken, Instant syncClaimedUntil, String syncError) { }
    record Event(long id, long ticketId, Long actorUserId, String action, int repairCycle, String note, Instant createdAt) {
        @Override public String toString() { return "TicketEvent[id=" + id + ",action=" + action + "]"; }
    }
    record Review(long ticketId, long reviewerId, int rating, String comment, Instant createdAt) {
        @Override public String toString() { return "Review[ticketId=" + ticketId + "]"; }
    }
    @Select("SELECT * FROM maintenance.repair_ticket WHERE id=#{id}") Ticket ticket(long id);
    @Select("SELECT * FROM maintenance.repair_ticket WHERE id=#{id} FOR UPDATE") Ticket lock(long id);
    @Select("SELECT * FROM maintenance.repair_ticket WHERE (#{view}='mine' AND reporter_id=#{actor}) "
            + "OR (#{view}='assigned' AND assignee_id=#{actor}) OR #{view}='managed' ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Ticket> list(@Param("view") String view, @Param("actor") long actor, @Param("limit") int limit, @Param("offset") int offset);
    @Select(value = "INSERT INTO maintenance.repair_ticket(reporter_id,room_id,description,status,reported_at,updated_at) "
            + "VALUES(#{actor},#{room},#{description},'REPORTED',#{now},#{now}) RETURNING *", affectData = true)
    Ticket insert(@Param("actor") long actor, @Param("room") long room, @Param("description") String description, @Param("now") Instant now);
    @Select("SELECT * FROM maintenance.ticket_event WHERE ticket_id=#{id} ORDER BY id") List<Event> events(long id);
    @Select("SELECT * FROM maintenance.ticket_review WHERE ticket_id=#{id}") Review review(long id);
    @Insert("INSERT INTO maintenance.ticket_event(ticket_id,actor_user_id,action,repair_cycle,note,created_at) "
            + "VALUES(#{id},#{actor},#{action},#{cycle},#{note},#{now})")
    int event(@Param("id") long id, @Param("actor") Long actor, @Param("action") String action,
            @Param("cycle") int cycle, @Param("note") String note, @Param("now") Instant now);
    @Update("UPDATE maintenance.repair_ticket SET assignee_id=#{assignee},status='ASSIGNED',due_at=#{due},repair_cycle=1,updated_at=#{now} WHERE id=#{id}")
    int assign(@Param("id") long id, @Param("assignee") long assignee, @Param("due") Instant due, @Param("now") Instant now);
    @Update("UPDATE maintenance.repair_ticket SET status=#{status},updated_at=#{now} WHERE id=#{id}")
    int state(@Param("id") long id, @Param("status") String status, @Param("now") Instant now);
    @Update("UPDATE maintenance.repair_ticket SET status='IN_PROGRESS',repair_cycle=repair_cycle+1,escalated=false,due_at=#{due},updated_at=#{now} WHERE id=#{id}")
    int rework(@Param("id") long id, @Param("due") Instant due, @Param("now") Instant now);
    @Update("UPDATE maintenance.repair_ticket SET status='BLOCKING_ROOM',block_start_at=#{now},sync_operation='BLOCK',sync_attempts=0,sync_next_attempt_at=#{now},updated_at=#{now} WHERE id=#{id}")
    int start(@Param("id") long id, @Param("now") Instant now);
    @Update("UPDATE maintenance.repair_ticket SET sync_operation='RELEASE',sync_attempts=0,sync_next_attempt_at=#{now},sync_error=NULL,updated_at=#{now} WHERE id=#{id}")
    int release(@Param("id") long id, @Param("now") Instant now);
    @Insert("INSERT INTO maintenance.ticket_review(ticket_id,reviewer_id,rating,comment,created_at) VALUES(#{id},#{actor},#{rating},#{comment},#{now})")
    int addReview(@Param("id") long id, @Param("actor") long actor, @Param("rating") int rating,
            @Param("comment") String comment, @Param("now") Instant now);
    @Select("SELECT id FROM maintenance.repair_ticket WHERE due_at<=#{now} AND NOT escalated "
            + "AND status IN ('ASSIGNED','BLOCKING_ROOM','IN_PROGRESS','SYNC_FAILED') ORDER BY due_at,id LIMIT 100")
    List<Long> overdue(Instant now);
    @Update("UPDATE maintenance.repair_ticket SET escalated=true,updated_at=#{now} WHERE id=#{id}")
    int escalate(@Param("id") long id, @Param("now") Instant now);
    @Select("SELECT id FROM maintenance.repair_ticket WHERE sync_operation IS NOT NULL AND status IN ('BLOCKING_ROOM','WAITING_ACCEPTANCE') "
            + "AND ((sync_claim_token IS NULL AND sync_next_attempt_at<=#{now}) OR sync_claimed_until<=#{now}) ORDER BY id LIMIT 100")
    List<Long> syncDue(Instant now);
    @Update("UPDATE maintenance.repair_ticket SET sync_claim_token=#{token},sync_claimed_until=#{until},sync_attempts=sync_attempts+1,updated_at=#{now} WHERE id=#{id}")
    int claim(@Param("id") long id, @Param("token") UUID token, @Param("until") Instant until, @Param("now") Instant now);
    @Update("UPDATE maintenance.repair_ticket SET status=#{status},occupancy_id=#{occupancy},sync_operation=NULL,sync_next_attempt_at=NULL,sync_claim_token=NULL,sync_claimed_until=NULL,sync_error=NULL,updated_at=#{now} WHERE id=#{id}")
    int confirmed(@Param("id") long id, @Param("status") String status, @Param("occupancy") long occupancy, @Param("now") Instant now);
    @Update("UPDATE maintenance.repair_ticket SET status=#{status},sync_error=#{error},sync_next_attempt_at=#{next},sync_claim_token=NULL,sync_claimed_until=NULL,updated_at=#{now} WHERE id=#{id}")
    int failed(@Param("id") long id, @Param("status") String status, @Param("error") String error,
            @Param("next") Instant next, @Param("now") Instant now);
}
