package cn.campus.resource.platform;

import java.time.Instant;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface CommonMapper {
    @Select("SELECT value FROM platform.system_setting WHERE key = 'max_reservation_hours'")
    int maxReservationHours();

    @Update("UPDATE platform.system_setting SET value = #{hours} WHERE key = 'max_reservation_hours'")
    int setMaxReservationHours(int hours);

    @Select("""
            SELECT c.code FROM platform.room_checkin_code c
            JOIN platform.room r ON r.id = c.room_id
            WHERE c.room_id = #{roomId} AND c.date = #{date} AND r.enabled
            """)
    String checkinCode(@Param("roomId") long roomId, @Param("date") LocalDate date);

    @Insert("""
            INSERT INTO platform.room_checkin_code (room_id, date, code, created_at)
            VALUES (#{roomId}, #{date}, #{code}, #{createdAt}) ON CONFLICT (room_id, date) DO NOTHING
            """)
    int insertCheckinCode(@Param("roomId") long roomId, @Param("date") LocalDate date,
            @Param("code") String code, @Param("createdAt") Instant createdAt);
}
