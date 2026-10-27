package cn.campus.resource.classroom;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.*;

@Mapper
interface ReleaseMapper {
    record Release(long requestId, String reason, String status, int attempts, Instant nextAttemptAt,
            UUID claimToken, Instant claimedUntil, String error) { }
    @Select("SELECT * FROM classroom.request_release WHERE request_id=#{id}") Release release(long id);
    @Insert("INSERT INTO classroom.request_release(request_id,reason,status,next_attempt_at) VALUES(#{id},#{reason},'PENDING',#{now})")
    int queue(@Param("id") long id,@Param("reason") String reason,@Param("now") Instant now);
    @Select("SELECT id FROM classroom.classroom_request WHERE status='APPROVED' AND end_at<=#{now} ORDER BY end_at,id LIMIT 100")
    List<Long> expired(Instant now);
    @Select("SELECT request_id FROM classroom.request_release WHERE status='PENDING' AND "
            + "((claim_token IS NULL AND next_attempt_at<=#{now}) OR claimed_until<=#{now}) ORDER BY request_id LIMIT 100")
    List<Long> due(Instant now);
    @Update("UPDATE classroom.request_release SET attempts=attempts+1,claim_token=#{token},claimed_until=#{until} WHERE request_id=#{id}")
    int claim(@Param("id") long id,@Param("token") UUID token,@Param("until") Instant until);
    @Update("UPDATE classroom.request_release SET status=#{status},error=#{error},next_attempt_at=#{next},claim_token=NULL,claimed_until=NULL WHERE request_id=#{id}")
    int finish(@Param("id") long id,@Param("status") String status,@Param("error") String error,@Param("next") Instant next);
}
