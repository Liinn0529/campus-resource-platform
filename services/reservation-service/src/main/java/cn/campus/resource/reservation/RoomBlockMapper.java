package cn.campus.resource.reservation;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface RoomBlockMapper {
    record Block(long id, long roomId, Instant startAt, Instant endAt, String sourceModule,
            String sourceType, String sourceRefId, String status) { }

    @Select("""
            SELECT id, room_id, start_at, end_at, source_module, source_type, source_ref_id, status
            FROM reservation.seat_occupancy
            WHERE source_module = #{module} AND source_type = #{type} AND source_ref_id = #{ref}
            """)
    Block source(@Param("module") String module, @Param("type") String type, @Param("ref") String ref);

    @Select("""
            SELECT EXISTS (SELECT 1 FROM reservation.seat_occupancy
              WHERE room_id = #{room} AND status = 'ACTIVE'
                AND (CAST(#{end} AS timestamptz) IS NULL OR start_at < #{end})
                AND (end_at IS NULL OR end_at > #{start}))
            """)
    boolean conflict(@Param("room") long room, @Param("start") Instant start, @Param("end") Instant end);

    @Select(value = """
            INSERT INTO reservation.seat_occupancy
                (room_id, start_at, end_at, source_module, source_type, source_ref_id, status, created_at)
            VALUES (#{room}, #{start}, #{end}, #{module}, #{type}, #{ref}, 'ACTIVE', #{now})
            ON CONFLICT (source_module, source_ref_id, source_type) DO NOTHING
            RETURNING id, room_id, start_at, end_at, source_module, source_type, source_ref_id, status
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Block insert(@Param("room") long room, @Param("start") Instant start, @Param("end") Instant end,
            @Param("module") String module, @Param("type") String type, @Param("ref") String ref,
            @Param("now") Instant now);

    @Update("""
            UPDATE reservation.seat_occupancy SET status = 'RELEASED', released_at = #{now}
            WHERE id = #{id} AND status = 'ACTIVE'
            """)
    int release(@Param("id") long id, @Param("now") Instant now);
}
