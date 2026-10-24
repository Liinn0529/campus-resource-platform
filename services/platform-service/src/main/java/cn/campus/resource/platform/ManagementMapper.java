package cn.campus.resource.platform;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface ManagementMapper {
    record UserRow(long id, String username, String displayName, boolean enabled) { }
    record RoleRow(long id, String code, String name) { }
    record PermissionRow(long id, String code, String name) { }

    @Select("""
            SELECT id, username, display_name AS "displayName", enabled FROM platform."user"
            ORDER BY id LIMIT #{limit} OFFSET #{offset}
            """)
    List<UserRow> users(@Param("limit") int limit, @Param("offset") int offset);

    @Select("""
            SELECT id, username, display_name AS "displayName", enabled
            FROM platform."user" WHERE id = #{id}
            """)
    UserRow user(long id);

    @Select("""
            SELECT id, username, display_name AS "displayName", enabled
            FROM platform."user" WHERE id = #{id} FOR UPDATE
            """)
    UserRow lockUser(long id);

    @Select("SELECT id FROM platform.role WHERE code = 'SYSTEM_ADMIN' FOR UPDATE")
    Long lockSystemAdminRole();

    @Select("""
            SELECT count(*) FROM platform."user" u
            JOIN platform.user_role ur ON ur.user_id = u.id
            JOIN platform.role r ON r.id = ur.role_id
            WHERE u.enabled AND r.code = 'SYSTEM_ADMIN'
            """)
    long enabledSystemAdmins();

    @Select("""
            SELECT r.code FROM platform.role r JOIN platform.user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{id} ORDER BY r.code
            """)
    List<String> userRoles(long id);

    @Select("SELECT room_id FROM platform.user_room_scope WHERE user_id = #{id} ORDER BY room_id")
    List<Long> userRooms(long id);

    @Select(value = """
            INSERT INTO platform."user" (username, display_name, password_hash)
            VALUES (#{username}, #{displayName}, #{passwordHash}) RETURNING id
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    long insertUser(@Param("username") String username, @Param("displayName") String displayName,
            @Param("passwordHash") String passwordHash);

    @Update("""
            UPDATE platform."user" SET display_name = #{displayName}, enabled = #{enabled},
                auth_version = auth_version + CASE WHEN enabled <> #{enabled} THEN 1 ELSE 0 END
            WHERE id = #{id}
            """)
    int updateProfile(@Param("id") long id, @Param("displayName") String displayName,
            @Param("enabled") boolean enabled);

    @Update("""
            UPDATE platform."user" SET password_hash = #{passwordHash}, auth_version = auth_version + 1
            WHERE id = #{id}
            """)
    int resetPassword(@Param("id") long id, @Param("passwordHash") String passwordHash);

    @Delete("DELETE FROM platform.user_role WHERE user_id = #{userId}")
    int deleteUserRoles(long userId);

    @Insert("""
            INSERT INTO platform.user_role (user_id, role_id)
            SELECT #{userId}, id FROM platform.role WHERE code = #{code}
            """)
    int insertUserRole(@Param("userId") long userId, @Param("code") String code);

    @Delete("DELETE FROM platform.user_room_scope WHERE user_id = #{userId}")
    int deleteUserRooms(long userId);

    @Select("SELECT EXISTS (SELECT 1 FROM platform.room WHERE id = #{id})")
    boolean roomExists(long id);

    @Insert("INSERT INTO platform.user_room_scope (user_id, room_id) VALUES (#{userId}, #{roomId})")
    int insertUserRoom(@Param("userId") long userId, @Param("roomId") long roomId);

    @Select("SELECT id, code, name FROM platform.role ORDER BY code")
    List<RoleRow> roles();

    @Select("SELECT id, code, name FROM platform.role WHERE code = #{code} FOR UPDATE")
    RoleRow lockRole(String code);

    @Select("SELECT id, code, name FROM platform.permission ORDER BY code")
    List<PermissionRow> permissions();

    @Select("""
            SELECT p.code FROM platform.permission p
            JOIN platform.role_permission rp ON rp.permission_id = p.id
            WHERE rp.role_id = #{roleId} ORDER BY p.code
            """)
    List<String> rolePermissions(long roleId);

    @Delete("DELETE FROM platform.role_permission WHERE role_id = #{roleId}")
    int deleteRolePermissions(long roleId);

    @Insert("""
            INSERT INTO platform.role_permission (role_id, permission_id)
            SELECT #{roleId}, id FROM platform.permission WHERE code = #{code}
            """)
    int insertRolePermission(@Param("roleId") long roleId, @Param("code") String code);
}
