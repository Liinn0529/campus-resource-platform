package cn.campus.resource.platform;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface SpaceMapper {
    record Building(long id, String code, String name) { }
    record Room(long id, long buildingId, String code, String name, String type, boolean enabled) { }
    record Seat(long id, long roomId, String code, boolean hasPowerOutlet, boolean enabled) { }

    @Select("SELECT id, code, name FROM platform.building ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<Building> buildings(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT id, code, name FROM platform.building WHERE id = #{id}")
    Building building(long id);

    @Select(value = """
            INSERT INTO platform.building (code, name) VALUES (#{code}, #{name})
            RETURNING id, code, name
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Building insertBuilding(@Param("code") String code, @Param("name") String name);

    @Update("UPDATE platform.building SET code = #{code}, name = #{name} WHERE id = #{id}")
    int updateBuilding(@Param("id") long id, @Param("code") String code, @Param("name") String name);

    @Select("""
            SELECT id, building_id AS "buildingId", code, name, type, enabled
            FROM platform.room
            WHERE enabled AND (#{buildingId}::bigint IS NULL OR building_id = #{buildingId})
            ORDER BY id LIMIT #{limit} OFFSET #{offset}
            """)
    List<Room> rooms(@Param("buildingId") Long buildingId, @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("""
            SELECT r.id, r.building_id AS "buildingId", r.code, r.name, r.type, r.enabled
            FROM platform.room r
            WHERE (#{allRooms} OR EXISTS (
                SELECT 1 FROM platform.user_room_scope s WHERE s.room_id = r.id AND s.user_id = #{userId}))
            AND (#{buildingId}::bigint IS NULL OR r.building_id = #{buildingId})
            ORDER BY r.id LIMIT #{limit} OFFSET #{offset}
            """)
    List<Room> managedRooms(@Param("userId") long userId, @Param("allRooms") boolean allRooms,
            @Param("buildingId") Long buildingId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT id, building_id AS "buildingId", code, name, type, enabled
            FROM platform.room WHERE id = #{id}
            """)
    Room room(long id);

    @Select("""
            SELECT id, building_id AS "buildingId", code, name, type, enabled
            FROM platform.room WHERE id = #{id} FOR UPDATE
            """)
    Room lockRoom(long id);

    @Select(value = """
            INSERT INTO platform.room (building_id, code, name, type, enabled)
            VALUES (#{buildingId}, #{code}, #{name}, #{type}, #{enabled})
            RETURNING id, building_id AS "buildingId", code, name, type, enabled
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Room insertRoom(@Param("buildingId") long buildingId, @Param("code") String code,
            @Param("name") String name, @Param("type") String type, @Param("enabled") boolean enabled);

    @Update("""
            UPDATE platform.room SET building_id = #{buildingId}, code = #{code}, name = #{name},
                type = #{type}, enabled = #{enabled} WHERE id = #{id}
            """)
    int updateRoom(Room room);

    @Select("""
            SELECT s.id, s.room_id AS "roomId", s.code, s.has_power_outlet AS "hasPowerOutlet", s.enabled
            FROM platform.seat s JOIN platform.room r ON r.id = s.room_id
            WHERE s.room_id = #{roomId} AND (#{includeDisabled} OR (s.enabled AND r.enabled))
            ORDER BY s.id LIMIT #{limit} OFFSET #{offset}
            """)
    List<Seat> seats(@Param("roomId") long roomId, @Param("includeDisabled") boolean includeDisabled,
            @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT id, room_id AS "roomId", code, has_power_outlet AS "hasPowerOutlet", enabled
            FROM platform.seat WHERE id = #{id} FOR UPDATE
            """)
    Seat lockSeat(long id);

    @Select(value = """
            INSERT INTO platform.seat (room_id, code, has_power_outlet, enabled)
            VALUES (#{roomId}, #{code}, #{hasPowerOutlet}, #{enabled})
            RETURNING id, room_id AS "roomId", code, has_power_outlet AS "hasPowerOutlet", enabled
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    Seat insertSeat(@Param("roomId") long roomId, @Param("code") String code,
            @Param("hasPowerOutlet") boolean hasPowerOutlet, @Param("enabled") boolean enabled);

    @Update("""
            UPDATE platform.seat SET room_id = #{roomId}, code = #{code},
                has_power_outlet = #{hasPowerOutlet}, enabled = #{enabled} WHERE id = #{id}
            """)
    int updateSeat(Seat seat);
}
