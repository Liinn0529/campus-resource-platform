package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SelfScopeApiTest extends ApiTestSupport {
    private static final String PASSWORD = "test-only-self-scope-password-2026";

    @Test
    void departmentScopesAreReadFromCurrentDatabaseWithTheSameUserJwt() {
        String admin = login("demo_admin");
        String name = uniqueUsername();
        long id = create(name, Set.of("DEPARTMENT_ADMIN"), admin);
        List<Long> rooms = rooms();
        try {
            String token = loginWith(name);
            assertThat(scopes(token)).isEmpty();
            replaceRooms(id, List.of(rooms.get(1), rooms.get(0)), admin);
            assertThat(scopes(token)).containsExactly(rooms.get(0), rooms.get(1));
            replaceRooms(id, List.of(rooms.get(1)), admin);
            assertThat(scopes(token)).containsExactly(rooms.get(1));
            replaceRooms(id, List.of(), admin);
            assertThat(scopes(token)).isEmpty();
        } finally {
            deleteUser(id);
        }
    }

    @Test
    void scopeSurvivesEitherApprovalRoleAndIsClearedWhenBothAreRemoved() {
        String admin = login("demo_admin");
        String name = uniqueUsername();
        long id = create(name, Set.of("ROOM_ADMIN", "DEPARTMENT_ADMIN"), admin);
        long room = rooms().getFirst();
        try {
            String token = loginWith(name);
            replaceRooms(id, List.of(room), admin);
            replaceRoles(id, Set.of("DEPARTMENT_ADMIN"), admin);
            assertThat(scopes(token)).containsExactly(room);
            replaceRoles(id, Set.of("ROOM_ADMIN"), admin);
            assertThat(scopes(token)).containsExactly(room);
            replaceRoles(id, Set.of("STUDENT"), admin);
            assertThat(request(HttpMethod.GET, "/api/v1/me/room-scopes", null, token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.user_room_scope WHERE user_id = ?",
                    Long.class, id)).isZero();
            assertThat(request(HttpMethod.PUT, "/api/v1/users/" + id + "/room-scopes",
                    Map.of("roomIds", List.of(room)), admin).getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        } finally {
            deleteUser(id);
        }
    }

    @Test
    void departmentApprovalScopeDoesNotGrantRoomMaintenanceOrCheckinCodeAccess() {
        String admin = login("demo_admin");
        String name = uniqueUsername();
        long id = create(name, Set.of("DEPARTMENT_ADMIN"), admin);
        long room = rooms().getFirst();
        try {
            replaceRooms(id, List.of(room), admin);
            String token = loginWith(name);
            assertThat(scopes(token)).containsExactly(room);
            assertThat(request(HttpMethod.PATCH, "/api/v1/rooms/" + room,
                    Map.of("name", "Forbidden department edit"), token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(request(HttpMethod.GET, "/api/v1/rooms/" + room + "/checkin-code", null, token)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(request(HttpMethod.POST, "/api/v1/rooms/" + room + "/checkin-code", null, token)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            deleteUser(id);
        }
    }

    @Test
    void selfScopeRequiresCurrentApprovalPermissionAndDoesNotSelectAnotherUsersScope() {
        String admin = login("demo_admin");
        String firstName = uniqueUsername();
        String secondName = uniqueUsername();
        long first = create(firstName, Set.of("DEPARTMENT_ADMIN"), admin);
        long second = create(secondName, Set.of("DEPARTMENT_ADMIN"), admin);
        List<Long> rooms = rooms();
        List<String> original = jdbc.queryForList("""
                SELECT p.code FROM platform.permission p JOIN platform.role_permission rp ON rp.permission_id = p.id
                JOIN platform.role r ON r.id = rp.role_id WHERE r.code = 'DEPARTMENT_ADMIN' ORDER BY p.code
                """, String.class);
        try {
            replaceRooms(first, List.of(rooms.get(0)), admin);
            replaceRooms(second, List.of(rooms.get(1)), admin);
            String token = loginWith(firstName);
            assertThat(scopes(token)).containsExactly(rooms.get(0));
            var forged = request(HttpMethod.GET, "/api/v1/me/room-scopes?userId=" + second, null, token);
            assertThat(forged.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(scopeData(forged)).containsExactly(rooms.get(0));
            assertThat(request(HttpMethod.PUT, "/api/v1/roles/DEPARTMENT_ADMIN/permissions",
                    Map.of("permissionCodes", List.of("space:view")), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.GET, "/api/v1/me/room-scopes", null, token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            assertThat(request(HttpMethod.PUT, "/api/v1/roles/DEPARTMENT_ADMIN/permissions",
                    Map.of("permissionCodes", original), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            deleteUser(first);
            deleteUser(second);
        }
    }

    @Test
    void anonymousStudentsAndInternalServiceTokensCannotReadUserScopes() {
        assertThat(request(HttpMethod.GET, "/api/v1/me/room-scopes", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(HttpMethod.GET, "/api/v1/me/room-scopes", null, CLASSROOM_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(HttpMethod.GET, "/api/v1/me/room-scopes", null, login("demo_student_01"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(scopes(login("demo_admin"))).isEmpty();
    }

    private List<Long> scopes(String token) {
        var response = request(HttpMethod.GET, "/api/v1/me/room-scopes", null, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return scopeData(response);
    }

    private List<Long> scopeData(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        Object value = response.getBody().get("data");
        assertThat(value).isInstanceOf(List.class);
        return ((List<?>) value).stream().map(item -> ((Number) item).longValue()).toList();
    }

    private List<Long> rooms() {
        return jdbc.queryForList("SELECT id FROM platform.room ORDER BY id", Long.class);
    }

    private long create(String username, Set<String> roles, String admin) {
        var response = request(HttpMethod.POST, "/api/v1/users", Map.of("username", username,
                "displayName", "Self scope test user", "password", PASSWORD, "roleCodes", roles), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) data(response).get("id")).longValue();
    }

    private String loginWith(String username) {
        var response = request(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", username, "password", PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) data(response).get("accessToken");
    }

    private void replaceRooms(long id, List<Long> rooms, String admin) {
        assertThat(request(HttpMethod.PUT, "/api/v1/users/" + id + "/room-scopes",
                Map.of("roomIds", rooms), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void replaceRoles(long id, Set<String> roles, String admin) {
        assertThat(request(HttpMethod.PUT, "/api/v1/users/" + id + "/roles",
                Map.of("roleCodes", roles), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void deleteUser(long id) {
        jdbc.update("DELETE FROM platform.user_room_scope WHERE user_id = ?", id);
        jdbc.update("DELETE FROM platform.user_role WHERE user_id = ?", id);
        jdbc.update("DELETE FROM platform.\"user\" WHERE id = ?", id);
    }

    private String uniqueUsername() { return "scope_" + UUID.randomUUID().toString().replace("-", ""); }
}
