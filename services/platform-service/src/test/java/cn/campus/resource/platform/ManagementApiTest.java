package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ManagementApiTest extends ApiTestSupport {
    private static final String PASSWORD = "test-only-management-password-2026";

    @Test
    void adminCreatesUpdatesAndResetsUserWithTransactionalAuditAndNoPasswordResponse() {
        String admin = login("demo_admin");
        String username = uniqueUsername();
        long id = create(username, Set.of("STUDENT"), admin);
        try {
            String encoded = jdbc.queryForObject("SELECT password_hash FROM platform.\"user\" WHERE id = ?",
                    String.class, id);
            assertThat(new BCryptPasswordEncoder().matches(PASSWORD, encoded)).isTrue();
            String originalToken = loginWith(username, PASSWORD);
            var update = request(HttpMethod.PATCH, "/api/v1/users/" + id,
                    Map.of("displayName", "Updated management test user"), admin);
            assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(update)).containsEntry("displayName", "Updated management test user");
            assertThat(data(update)).doesNotContainKeys("password", "passwordHash", "password_hash", "authVersion");

            String replacement = "test-only-replacement-password-2026";
            var reset = request(HttpMethod.PUT, "/api/v1/users/" + id + "/password",
                    Map.of("password", replacement), admin);
            assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.GET, "/api/v1/me", null, originalToken).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(request(HttpMethod.POST, "/api/v1/auth/login",
                    Map.of("username", username, "password", PASSWORD), null).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            String replacementToken = loginWith(username, replacement);
            assertThat(request(HttpMethod.GET, "/api/v1/me", null, replacementToken).getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            Map<String, Object> event = jdbc.queryForMap("""
                    SELECT action, target_type, target_id, request_id::text AS request_id
                    FROM platform.audit_log WHERE action = 'USER_PASSWORD_RESET' AND target_id = ?
                    """, Long.toString(id));
            assertThat(event).containsEntry("target_type", "USER")
                    .containsEntry("request_id", reset.getBody().get("requestId"));
            assertThat(event.toString()).doesNotContain(PASSWORD, replacement, username, encoded);

            var disabled = request(HttpMethod.PATCH, "/api/v1/users/" + id, Map.of("enabled", false), admin);
            assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.GET, "/api/v1/me", null, replacementToken).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            deleteUnusedUser(id);
        }
    }

    @Test
    void studentCannotManageUsersRolesPermissionsOrReadAudits() {
        String student = login("demo_student_01");
        for (String path : List.of("/api/v1/users", "/api/v1/roles", "/api/v1/permissions",
                "/api/v1/audit-events")) {
            assertThat(request(HttpMethod.GET, path, null, student).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(request(HttpMethod.POST, "/api/v1/users", Map.of("username", uniqueUsername(),
                "displayName", "Forbidden test user", "password", PASSWORD,
                "roleCodes", List.of("STUDENT")), student).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(HttpMethod.PUT, "/api/v1/users/" + userid("demo_admin") + "/roles",
                Map.of("roleCodes", List.of("STUDENT")), student).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidReferencesAndPaginationFailWithoutCreatingUsers() {
        String admin = login("demo_admin");
        String username = uniqueUsername();
        var invalid = request(HttpMethod.POST, "/api/v1/users", Map.of("username", username,
                "displayName", "Invalid role test", "password", PASSWORD,
                "roleCodes", List.of("UNKNOWN_ROLE")), admin);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.\"user\" WHERE username = ?",
                Long.class, username)).isZero();
        for (String path : List.of("/api/v1/users?limit=101", "/api/v1/users?offset=-1",
                "/api/v1/audit-events?limit=0")) {
            assertThat(request(HttpMethod.GET, path, null, admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(request(HttpMethod.PUT, "/api/v1/roles/SYSTEM_ADMIN/permissions",
                Map.of("permissionCodes", List.of()), admin).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(request(HttpMethod.PUT, "/api/v1/roles/OPERATOR/permissions",
                Map.of("permissionCodes", List.of("unknown:permission")), admin).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(request(HttpMethod.PATCH, "/api/v1/users/" + userid("demo_admin"), Map.of(), admin)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void lastEnabledAdministratorCannotBeDisabledOrLoseTheRole() {
        String admin = login("demo_admin");
        long id = userid("demo_admin");
        long before = jdbc.queryForObject("SELECT count(*) FROM platform.audit_log", Long.class);
        var disabled = request(HttpMethod.PATCH, "/api/v1/users/" + id, Map.of("enabled", false), admin);
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(disabled.getBody()).containsEntry("code", "LAST_SYSTEM_ADMIN");
        var removed = request(HttpMethod.PUT, "/api/v1/users/" + id + "/roles",
                Map.of("roleCodes", List.of("STUDENT")), admin);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.audit_log", Long.class)).isEqualTo(before);
    }

    @Test
    void roomScopesRejectNonApprovalRolesAndAreClearedWhenTheRoleIsRemoved() {
        String admin = login("demo_admin");
        long id = create(uniqueUsername(), Set.of("ROOM_ADMIN"), admin);
        long roomId = jdbc.queryForObject("SELECT min(id) FROM platform.room", Long.class);
        try {
            assertThat(request(HttpMethod.PUT, "/api/v1/users/" + id + "/room-scopes",
                    Map.of("roomIds", List.of(Long.MAX_VALUE)), admin).getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            var assigned = request(HttpMethod.PUT, "/api/v1/users/" + id + "/room-scopes",
                    Map.of("roomIds", List.of(roomId)), admin);
            assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(assigned).get("roomIds")).isEqualTo(List.of((int) roomId));
            var removed = request(HttpMethod.PUT, "/api/v1/users/" + id + "/roles",
                    Map.of("roleCodes", List.of("STUDENT")), admin);
            assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat((List<?>) data(removed).get("roomIds")).isEmpty();
            assertThat(request(HttpMethod.PUT, "/api/v1/users/" + id + "/room-scopes",
                    Map.of("roomIds", List.of(roomId)), admin).getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        } finally {
            deleteUnusedUser(id);
        }
    }

    @Test
    void rolePermissionEditsAreAuditedAndAuditRowsCannotBeChangedByAppIdentity() {
        String admin = login("demo_admin");
        List<String> original = jdbc.queryForList("""
                SELECT p.code FROM platform.permission p JOIN platform.role_permission rp ON rp.permission_id = p.id
                JOIN platform.role r ON r.id = rp.role_id WHERE r.code = 'OPERATOR' ORDER BY p.code
                """, String.class);
        try {
            var updated = request(HttpMethod.PUT, "/api/v1/roles/OPERATOR/permissions",
                    Map.of("permissionCodes", List.of("space:view")), admin);
            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(updated).get("permissionCodes")).isEqualTo(List.of("space:view"));
            var events = request(HttpMethod.GET, "/api/v1/audit-events?targetType=ROLE&limit=100", null, admin);
            assertThat(events.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) events.getBody().get("data");
            assertThat(rows).anySatisfy(row -> {
                assertThat(row).containsEntry("targetId", "OPERATOR")
                        .containsEntry("action", "ROLE_PERMISSIONS_REPLACED")
                        .containsEntry("requestId", updated.getBody().get("requestId"));
                assertThat(row.keySet()).containsExactlyInAnyOrder("id", "actorUserId", "sourceService",
                        "action", "targetType", "targetId", "requestId", "occurredAt");
            });
            assertThatThrownBy(() -> jdbc.update("UPDATE platform.audit_log SET action = action WHERE false"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM platform.audit_log WHERE false"))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            assertThat(request(HttpMethod.PUT, "/api/v1/roles/OPERATOR/permissions",
                    Map.of("permissionCodes", original), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void concurrentDisablesRetainOneEnabledAdministrator() throws Exception {
        String admin = login("demo_admin");
        String firstName = uniqueUsername();
        String secondName = uniqueUsername();
        long first = create(firstName, Set.of("SYSTEM_ADMIN"), admin);
        long second = create(secondName, Set.of("SYSTEM_ADMIN"), admin);
        String firstToken = loginWith(firstName, PASSWORD);
        String secondToken = loginWith(secondName, PASSWORD);
        long demoAdmin = userid("demo_admin");
        try {
            assertThat(request(HttpMethod.PATCH, "/api/v1/users/" + demoAdmin,
                    Map.of("enabled", false), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var firstResult = workers.submit(() -> disableConcurrently(first, firstToken, ready, start));
                var secondResult = workers.submit(() -> disableConcurrently(second, secondToken, ready, start));
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                var results = List.of(firstResult.get(20, TimeUnit.SECONDS), secondResult.get(20, TimeUnit.SECONDS));
                assertThat(results.stream().filter(status -> status == HttpStatus.OK).count()).isEqualTo(1);
                assertThat(results).allMatch(status -> status == HttpStatus.OK
                        || status == HttpStatus.CONFLICT || status == HttpStatus.UNAUTHORIZED);
            }
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM platform."user" u JOIN platform.user_role ur ON ur.user_id = u.id
                    JOIN platform.role r ON r.id = ur.role_id WHERE u.enabled AND r.code = 'SYSTEM_ADMIN'
                    """, Long.class)).isEqualTo(1);
        } finally {
            // Audit actor references are retained; only these ephemeral test users are disabled for later tests.
            jdbc.update("UPDATE platform.\"user\" SET enabled = true, auth_version = auth_version + 1 WHERE id = ?",
                    demoAdmin);
            jdbc.update("UPDATE platform.\"user\" SET enabled = false, auth_version = auth_version + 1 WHERE id IN (?, ?)",
                    first, second);
        }
    }

    private HttpStatus disableConcurrently(long id, String token, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent requests did not start");
        }
        return (HttpStatus) request(HttpMethod.PATCH, "/api/v1/users/" + id,
                Map.of("enabled", false), token).getStatusCode();
    }

    private long create(String username, Set<String> roles, String admin) {
        ResponseEntity<Map> result = request(HttpMethod.POST, "/api/v1/users", Map.of("username", username,
                "displayName", "Management test user", "password", PASSWORD, "roleCodes", roles), admin);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(result)).doesNotContainKeys("password", "passwordHash", "password_hash");
        return ((Number) data(result).get("id")).longValue();
    }

    private String loginWith(String username, String password) {
        var result = request(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", username, "password", password), null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) data(result).get("accessToken");
    }

    private void deleteUnusedUser(long id) {
        jdbc.update("DELETE FROM platform.user_room_scope WHERE user_id = ?", id);
        jdbc.update("DELETE FROM platform.user_role WHERE user_id = ?", id);
        jdbc.update("DELETE FROM platform.\"user\" WHERE id = ?", id);
    }

    private String uniqueUsername() {
        return "test_" + UUID.randomUUID().toString().replace("-", "");
    }
}
