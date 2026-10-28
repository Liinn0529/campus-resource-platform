package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class ExternalAuditApiTest extends ApiTestSupport {
    private static final String TEST_PASSWORD = "test-only-audit-user-password-2026";

    @Test
    void onlyCurrentPropertyManagersCanListEnabledStaffAndOnlyMinimalFields() {
        String admin = login("demo_admin");
        String managerName = username("manager");
        String staffName = username("staff");
        String disabledName = username("disabled");
        String operatorName = username("operator");
        long managerId = createUser(managerName, "PROPERTY_MANAGER", admin);
        long staffId = createUser(staffName, "PROPERTY_STAFF", admin);
        long disabledId = createUser(disabledName, "PROPERTY_STAFF", admin);
        long studentId = createUser(username("student"), "STUDENT", admin);
        long operatorId = createUser(operatorName, "OPERATOR", admin);
        List<String> managerPermissions = rolePermissions("PROPERTY_MANAGER");
        List<String> operatorPermissions = rolePermissions("OPERATOR");
        try {
            assertThat(managerPermissions).contains("maintenance:assign");
            jdbc.update("UPDATE platform.\"user\" SET enabled=false WHERE id=?", disabledId);
            String manager = loginWith(managerName, TEST_PASSWORD);
            String staff = loginWith(staffName, TEST_PASSWORD);
            String operator = loginWith(operatorName, TEST_PASSWORD);
            var response = request(HttpMethod.GET, "/api/v1/maintenance-staff?limit=100&offset=0", null, manager);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> rows = rows(response);
            assertThat(rows).anySatisfy(row -> {
                assertThat(((Number) row.get("id")).longValue()).isEqualTo(staffId);
                assertThat(row).containsEntry("displayName", "Audit API test user")
                        .containsOnlyKeys("id", "displayName");
            });
            assertThat(rows).allSatisfy(row -> assertThat(row).containsOnlyKeys("id", "displayName"));
            List<Long> ids = rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
            assertThat(ids).isSorted();
            assertThat(rows).noneSatisfy(row -> assertThat(((Number) row.get("id")).longValue())
                    .isIn(disabledId, studentId));
            assertThat(request(HttpMethod.GET, "/api/v1/maintenance-staff", null, login("demo_admin"))
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(request(HttpMethod.GET, "/api/v1/maintenance-staff", null, login("demo_student_01"))
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(request(HttpMethod.GET, "/api/v1/maintenance-staff", null, staff)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            var firstPage = request(HttpMethod.GET, "/api/v1/maintenance-staff?limit=1", null, manager);
            assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> page = rows(firstPage);
            assertThat(page).hasSize(1);
            assertThat(((Number) page.getFirst().get("id")).longValue())
                    .isEqualTo(rows.stream().map(row -> ((Number) row.get("id")).longValue()).min(Long::compareTo).orElseThrow());
            var secondPage = request(HttpMethod.GET, "/api/v1/maintenance-staff?limit=1&offset=1", null, manager);
            assertThat(secondPage.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(rows(secondPage)).hasSize(1);
            assertThat(((Number) rows(secondPage).getFirst().get("id")).longValue())
                    .isGreaterThan(((Number) page.getFirst().get("id")).longValue());
            for (String query : List.of("limit=0", "limit=101", "offset=-1")) {
                assertThat(request(HttpMethod.GET, "/api/v1/maintenance-staff?" + query, null, manager)
                        .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            }

            List<String> operatorWithAssign = new java.util.ArrayList<>(operatorPermissions);
            operatorWithAssign.add("maintenance:assign");
            assertThat(request(HttpMethod.PUT, "/api/v1/roles/OPERATOR/permissions",
                    Map.of("permissionCodes", operatorWithAssign), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.GET, "/api/v1/maintenance-staff", null, operator)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            List<String> managerWithoutAssign = managerPermissions.stream()
                    .filter(permission -> !permission.equals("maintenance:assign")).toList();
            assertThat(request(HttpMethod.PUT, "/api/v1/roles/PROPERTY_MANAGER/permissions",
                    Map.of("permissionCodes", managerWithoutAssign), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.GET, "/api/v1/maintenance-staff", null, manager)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            request(HttpMethod.PUT, "/api/v1/roles/PROPERTY_MANAGER/permissions",
                    Map.of("permissionCodes", managerPermissions), admin);
            request(HttpMethod.PUT, "/api/v1/roles/OPERATOR/permissions",
                    Map.of("permissionCodes", operatorPermissions), admin);
            deleteUser(managerId);
            deleteUser(staffId);
            deleteUser(disabledId);
            deleteUser(studentId);
            deleteUser(operatorId);
        }
    }

    @Test
    void auditReceiverRequiresCredentialToMatchModuleAndEnforcesActionAndEntityAllowlist() {
        var event = event("M1", "CREATED", "RESERVATION", "reservation:7001");
        assertThat(receive(event, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(receive(event, login("demo_admin")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(receive(event, CLASSROOM_TOKEN).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(receive(event("M2", "SUBMITTED", "CLASSROOM_REQUEST", "classroom-request:1001"),
                CLASSROOM_TOKEN).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(receive(event("M3", "REPORTED", "MAINTENANCE_TICKET", "maintenance-ticket:1001"),
                MAINTENANCE_TOKEN).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var badAction = event("M1", "ARBITRARY_ACTION", "RESERVATION", "reservation:7002");
        assertThat(receive(badAction, RESERVATION_TOKEN).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        var wrongEntity = event("M1", "CREATED", "MAINTENANCE_TICKET", "maintenance-ticket:7002");
        assertThat(receive(wrongEntity, RESERVATION_TOKEN).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        var malformed = new HashMap<>(event);
        malformed.put("sourceModule", "OTHER");
        assertThat(receive(malformed, RESERVATION_TOKEN).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void identicalAuditReplayReusesOneRecordAndChangedPayloadConflicts() {
        Map<String, Object> event = event("M1", "CREATED", "RESERVATION", "reservation:7101");
        var first = receive(event, RESERVATION_TOKEN);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(first)).containsEntry("status", "ACCEPTED").containsOnlyKeys("id", "status");
        var repeated = receive(event, RESERVATION_TOKEN);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(repeated)).containsEntry("id", data(first).get("id"));
        var sameInstantDifferentOffset = new HashMap<>(event);
        sameInstantDifferentOffset.put("occurredAt", "2026-10-27T01:00:00.123456789Z");
        assertThat(receive(sameInstantDifferentOffset, RESERVATION_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        var sameStoredMicros = new HashMap<>(event);
        sameStoredMicros.put("occurredAt", "2026-10-27T09:00:00.123456999+08:00");
        assertThat(receive(sameStoredMicros, RESERVATION_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        String sourceEventId = (String) event.get("sourceEventId");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.audit_log "
                + "WHERE source_service='reservation-service' AND source_event_id=?", Long.class, sourceEventId))
                .isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT action, target_id FROM platform.audit_log "
                + "WHERE source_service='reservation-service' AND source_event_id=?", sourceEventId))
                .containsEntry("action", "CREATED").containsEntry("target_id", "reservation:7101");
        assertThat(jdbc.queryForObject("SELECT source_event_id FROM platform.audit_log WHERE id=?", String.class,
                ((Number) data(first).get("id")).longValue())).isEqualTo(sourceEventId);

        var changed = new HashMap<>(event);
        changed.put("entityId", "reservation:other");
        var conflict = receive(changed, RESERVATION_TOKEN);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).containsEntry("code", "AUDIT_EVENT_SOURCE_CONFLICT");
        var changedMicros = new HashMap<>(event);
        changedMicros.put("occurredAt", "2026-10-27T09:00:00.123457000+08:00");
        assertThat(receive(changedMicros, RESERVATION_TOKEN).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var changedAction = new HashMap<>(event);
        changedAction.put("action", "CANCELLED");
        assertThat(receive(changedAction, RESERVATION_TOKEN).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var changedActor = new HashMap<>(event);
        changedActor.put("actorUserId", userId("demo_admin"));
        assertThat(receive(changedActor, RESERVATION_TOKEN).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var changedRequest = new HashMap<>(event);
        changedRequest.put("sourceRequestId", UUID.randomUUID().toString());
        assertThat(receive(changedRequest, RESERVATION_TOKEN).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.audit_log "
                + "WHERE source_service='reservation-service' AND source_event_id=?", Long.class, sourceEventId))
                .isEqualTo(1);
    }

    @Test
    void concurrentIdenticalAndConflictingReplaysKeepOneImmutableReceipt() throws Exception {
        var identical = event("M1", "CREATED", "RESERVATION", "reservation:concurrent-same");
        var identicalResponses = concurrentReceive(List.of(identical, identical, identical, identical,
                identical, identical, identical, identical), RESERVATION_TOKEN);
        assertThat(identicalResponses).extracting(response -> response.getStatusCode())
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK,
                        HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK);
        Object receiptId = data(identicalResponses.getFirst()).get("id");
        assertThat(identicalResponses).allSatisfy(response -> assertThat(data(response).get("id")).isEqualTo(receiptId));
        String identicalEventId = (String) identical.get("sourceEventId");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.audit_log "
                + "WHERE source_service='reservation-service' AND source_event_id=?", Long.class, identicalEventId))
                .isEqualTo(1);

        var firstPayload = event("M1", "CREATED", "RESERVATION", "reservation:concurrent-conflict-a");
        var secondPayload = new HashMap<>(firstPayload);
        secondPayload.put("entityId", "reservation:concurrent-conflict-b");
        var conflictingResponses = concurrentReceive(List.of(firstPayload, secondPayload), RESERVATION_TOKEN);
        assertThat(conflictingResponses).extracting(response -> response.getStatusCode())
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        String conflictingEventId = (String) firstPayload.get("sourceEventId");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.audit_log "
                + "WHERE source_service='reservation-service' AND source_event_id=?", Long.class, conflictingEventId))
                .isEqualTo(1);
    }

    private Map<String, Object> event(String module, String action, String entityType, String entityId) {
        return Map.of("sourceModule", module,
                "sourceEventId", "test-event:" + UUID.randomUUID(),
                "action", action,
                "entityType", entityType,
                "entityId", entityId,
                "actorUserId", userId("demo_student_01"),
                "occurredAt", "2026-10-27T09:00:00.123456789+08:00",
                "sourceRequestId", UUID.randomUUID().toString());
    }

    private org.springframework.http.ResponseEntity<Map> receive(Map<String, Object> event, String credential) {
        return internalRequest(HttpMethod.POST, "/internal/v1/audit-events", event, credential);
    }

    private List<org.springframework.http.ResponseEntity<Map>> concurrentReceive(
            List<? extends Map<String, Object>> events, String credential) throws Exception {
        var ready = new CountDownLatch(events.size());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(events.size())) {
            List<Future<org.springframework.http.ResponseEntity<Map>>> futures = events.stream()
                    .map(event -> executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start gate timed out");
                        return receive(event, credential);
                    })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return futures.stream().map(future -> {
                try { return future.get(20, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException("Concurrent audit request failed", failure); }
            }).toList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(org.springframework.http.ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        return (List<Map<String, Object>>) response.getBody().get("data");
    }

    private long createUser(String username, String role, String admin) {
        var response = request(HttpMethod.POST, "/api/v1/users", Map.of("username", username,
                "displayName", "Audit API test user", "password", TEST_PASSWORD, "roleCodes", List.of(role)), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) data(response).get("id")).longValue();
    }

    private String loginWith(String username, String password) {
        var response = request(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", username, "password", password), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) data(response).get("accessToken");
    }

    private String username(String prefix) {
        return "test_" + prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void deleteUser(long id) {
        jdbc.update("DELETE FROM platform.user_room_scope WHERE user_id=?", id);
        jdbc.update("DELETE FROM platform.user_role WHERE user_id=?", id);
        jdbc.update("DELETE FROM platform.\"user\" WHERE id=?", id);
    }

    private List<String> rolePermissions(String roleCode) {
        return jdbc.queryForList("""
                SELECT p.code FROM platform.permission p
                JOIN platform.role_permission rp ON rp.permission_id = p.id
                JOIN platform.role r ON r.id = rp.role_id
                WHERE r.code = ? ORDER BY p.code
                """, String.class, roleCode);
    }
}
