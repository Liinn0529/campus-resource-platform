package cn.campus.resource.platform;

import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface IdentityMapper {
    @Select("SELECT id, username, display_name, password_hash, enabled, auth_version "
            + "FROM platform.\"user\" WHERE username = #{username}")
    UserAccount byUsername(String username);

    @Select("SELECT id, username, display_name, password_hash, enabled, auth_version "
            + "FROM platform.\"user\" WHERE id = #{id}")
    UserAccount byId(long id);

    @Select("SELECT r.code FROM platform.role r JOIN platform.user_role ur ON ur.role_id = r.id "
            + "WHERE ur.user_id = #{userId}")
    Set<String> roles(long userId);

    @Select("SELECT DISTINCT p.code FROM platform.permission p "
            + "JOIN platform.role_permission rp ON rp.permission_id = p.id "
            + "JOIN platform.user_role ur ON ur.role_id = rp.role_id WHERE ur.user_id = #{userId}")
    Set<String> permissions(long userId);

    @Select("SELECT EXISTS(SELECT 1 FROM platform.user_room_scope WHERE user_id = #{userId} AND room_id = #{roomId})")
    boolean hasRoomScope(@Param("userId") long userId, @Param("roomId") long roomId);

    @Update("UPDATE platform.\"user\" SET password_hash = #{hash}, auth_version = auth_version + 1 "
            + "WHERE id = #{id} AND auth_version = #{version} AND enabled = TRUE")
    int changePassword(@Param("id") long id, @Param("version") long version, @Param("hash") String hash);
}
