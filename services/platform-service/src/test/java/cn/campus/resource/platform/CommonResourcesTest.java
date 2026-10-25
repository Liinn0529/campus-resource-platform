package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Import(CommonResourcesTest.TimeConfiguration.class)
class CommonResourcesTest extends ApiTestSupport {
    @Autowired
    private AdjustableClock testClock;

    @Test
    void studentsCanReadCatalogButCannotManageSpacesSettingsOrCodes() {
        String student = login("demo_student_01");
        long roomId = firstRoom();
        assertThat(request(HttpMethod.GET, "/api/v1/buildings", null, student).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, "/api/v1/rooms?limit=1", null, student).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, "/api/v1/rooms/" + roomId + "/seats", null, student).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.POST, "/api/v1/buildings", Map.of("code", "FORBIDDEN", "name", "Forbidden"),
                student).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(HttpMethod.PATCH, "/api/v1/rooms/" + roomId, Map.of("name", "Forbidden"),
                student).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(HttpMethod.PUT, "/api/v1/settings", Map.of("maxReservationHours", 8),
                student).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(HttpMethod.GET, "/api/v1/rooms/" + roomId + "/checkin-code", null,
                student).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(HttpMethod.POST, "/api/v1/rooms/" + roomId + "/checkin-code", null,
                student).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void roomAdministratorsCannotEscapeTheirExplicitRoomScope() {
        long adminId = userid("demo_room_admin");
        var previousScope = jdbc.queryForList(
                "SELECT room_id FROM platform.user_room_scope WHERE user_id = ?", Long.class, adminId);
        var roomIds = jdbc.queryForList("SELECT id FROM platform.room ORDER BY id LIMIT 2", Long.class);
        long own = roomIds.get(0);
        long other = roomIds.get(1);
        String originalName = jdbc.queryForObject("SELECT name FROM platform.room WHERE id = ?", String.class, own);
        try {
            jdbc.update("DELETE FROM platform.user_room_scope WHERE user_id = ?", adminId);
            jdbc.update("INSERT INTO platform.user_room_scope (user_id, room_id) VALUES (?, ?)", adminId, own);
            String token = login("demo_room_admin");
            assertThat(request(HttpMethod.PATCH, "/api/v1/rooms/" + own, Map.of("name", "Scoped rename"), token)
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.PATCH, "/api/v1/rooms/" + other, Map.of("name", "Forbidden"), token)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(request(HttpMethod.PATCH, "/api/v1/rooms/" + own, Map.of("type", "CLASSROOM"), token)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(request(HttpMethod.POST, "/api/v1/buildings", Map.of("code", "DENIED", "name", "Denied"), token)
                    .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            var response = request(HttpMethod.GET, "/api/v1/managed-rooms", null, token);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(rows(response)).extracting(row -> ((Number) row.get("id")).longValue()).containsExactly(own);
            assertThat(jdbc.queryForObject("SELECT name FROM platform.room WHERE id = ?", String.class, own))
                    .isEqualTo("Scoped rename");
        } finally {
            jdbc.update("UPDATE platform.room SET name = ? WHERE id = ?", originalName, own);
            jdbc.update("DELETE FROM platform.user_room_scope WHERE user_id = ?", adminId);
            previousScope.forEach(id -> jdbc.update(
                    "INSERT INTO platform.user_room_scope (user_id, room_id) VALUES (?, ?)", adminId, id));
        }
    }

    @Test
    void administratorsMaintainRealSpacesAndDisabledRoomsDisappearFromStudentCatalog() {
        String admin = login("demo_admin");
        String student = login("demo_student_01");
        long buildingId = createBuilding(admin);
        Long roomId = null;
        try {
            roomId = createRoom(admin, buildingId);
            var seat = request(HttpMethod.POST, "/api/v1/rooms/" + roomId + "/seats",
                    Map.of("code", "S01", "hasPowerOutlet", true, "enabled", true), admin);
            assertThat(seat.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            long seatId = ((Number) data(seat).get("id")).longValue();
            assertThat(request(HttpMethod.PUT, "/api/v1/buildings/" + buildingId,
                    Map.of("code", "UPDATED_" + buildingId, "name", "Updated building"), admin).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.PATCH, "/api/v1/seats/" + seatId,
                    Map.of("hasPowerOutlet", false, "enabled", false), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.PATCH, "/api/v1/rooms/" + roomId, Map.of("enabled", false), admin)
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.GET, "/api/v1/rooms/" + roomId, null, student).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            var internal = internalRequest(HttpMethod.GET, "/internal/v1/rooms/" + roomId, null, RESERVATION_TOKEN);
            assertThat(internal.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(internal).get("enabled")).isEqualTo(false);
            var internalSeats = internalRequest(HttpMethod.GET, "/internal/v1/rooms/" + roomId + "/seats", null,
                    RESERVATION_TOKEN);
            assertThat(rows(internalSeats)).hasSize(1);
            assertThat(rows(internalSeats).get(0)).containsEntry("enabled", false).containsEntry("hasPowerOutlet", false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.audit_log WHERE action = 'ROOM_CREATE'"
                    + " AND target_id = ?", Long.class, Long.toString(roomId))).isEqualTo(1);
        } finally {
            deleteTestSpaces(buildingId, roomId);
        }
    }

    @Test
    void settingsChangesAreAuthorizedPersistedAndSharedThroughTheInternalContract() {
        String admin = login("demo_admin");
        int original = jdbc.queryForObject(
                "SELECT value FROM platform.system_setting WHERE key = 'max_reservation_hours'", Integer.class);
        try {
            var response = request(HttpMethod.PUT, "/api/v1/settings", Map.of("maxReservationHours", 6), admin);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(response)).containsEntry("maxReservationHours", 6);
            assertThat(data(internalRequest(HttpMethod.GET, "/internal/v1/settings", null, RESERVATION_TOKEN)))
                    .containsEntry("maxReservationHours", 6);
            assertThat(request(HttpMethod.PUT, "/api/v1/settings", Map.of("maxReservationHours", 0), admin)
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            jdbc.update("UPDATE platform.system_setting SET value = ? WHERE key = 'max_reservation_hours'", original);
        }
    }

    @Test
    void dailyCodeIsStableWithinTheBusinessDateAndInternalVerificationDoesNotExposeIt() {
        Instant originalTime = testClock.instant();
        testClock.set(Instant.parse("2026-10-23T15:59:30Z"));
        String admin = login("demo_admin");
        long buildingId = createBuilding(admin);
        Long roomId = null;
        try {
            roomId = createRoom(admin, buildingId);
            String path = "/api/v1/rooms/" + roomId + "/checkin-code";
            var first = request(HttpMethod.POST, path, null, admin);
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
            String code = (String) data(first).get("code");
            assertThat(code).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
            assertThat(data(first).get("date")).isEqualTo("2026-10-23");
            assertThat(data(request(HttpMethod.POST, path, null, admin))).isEqualTo(data(first));
            assertThat(data(request(HttpMethod.GET, path, null, admin))).isEqualTo(data(first));
            var correct = internalRequest(HttpMethod.POST, "/internal/v1/checkin-codes/verify",
                    Map.of("roomId", roomId, "code", code), RESERVATION_TOKEN);
            assertThat(data(correct)).containsExactlyEntriesOf(Map.of("valid", true));
            String wrong = (code.charAt(0) == 'A' ? "B" : "A") + code.substring(1);
            assertThat(data(internalRequest(HttpMethod.POST, "/internal/v1/checkin-codes/verify",
                    Map.of("roomId", roomId, "code", wrong), RESERVATION_TOKEN)))
                    .containsExactlyEntriesOf(Map.of("valid", false));

            testClock.set(Instant.parse("2026-10-23T16:00:30Z"));
            assertThat(data(internalRequest(HttpMethod.POST, "/internal/v1/checkin-codes/verify",
                    Map.of("roomId", roomId, "code", code), RESERVATION_TOKEN)))
                    .containsExactlyEntriesOf(Map.of("valid", false));
            assertThat(request(HttpMethod.GET, path, null, admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            var nextDay = request(HttpMethod.POST, path, null, admin);
            assertThat(nextDay.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(nextDay).get("date")).isEqualTo("2026-10-24");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.room_checkin_code WHERE room_id = ?",
                    Long.class, roomId)).isEqualTo(2);
            String todayCode = (String) data(nextDay).get("code");
            assertThat(request(HttpMethod.PATCH, "/api/v1/rooms/" + roomId, Map.of("enabled", false), admin)
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(request(HttpMethod.POST, path, null, admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(data(internalRequest(HttpMethod.POST, "/internal/v1/checkin-codes/verify",
                    Map.of("roomId", roomId, "code", todayCode), RESERVATION_TOKEN)))
                    .containsExactlyEntriesOf(Map.of("valid", false));
        } finally {
            testClock.set(originalTime);
            deleteTestSpaces(buildingId, roomId);
        }
    }

    @Test
    void concurrentCodeRequestsCreateOneDailyRecordAndOneAuditEvent() throws Exception {
        String admin = login("demo_admin");
        long buildingId = createBuilding(admin);
        Long roomId = null;
        try {
            roomId = createRoom(admin, buildingId);
            String path = "/api/v1/rooms/" + roomId + "/checkin-code";
            try (var executor = Executors.newFixedThreadPool(4)) {
                var tasks = java.util.stream.IntStream.range(0, 8)
                        .<java.util.concurrent.Callable<ResponseEntity<Map>>>mapToObj(index ->
                                () -> request(HttpMethod.POST, path, null, admin)).toList();
                var codes = executor.invokeAll(tasks).stream().map(result -> {
                    try {
                        var response = result.get();
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        return data(response).get("code");
                    } catch (Exception error) {
                        throw new AssertionError(error);
                    }
                }).toList();
                assertThat(codes.stream().distinct().count()).isEqualTo(1);
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.room_checkin_code WHERE room_id = ?",
                    Long.class, roomId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.audit_log"
                    + " WHERE action = 'CHECKIN_CODE_CREATE' AND target_id = ?", Long.class,
                    Long.toString(roomId))).isEqualTo(1);
        } finally {
            deleteTestSpaces(buildingId, roomId);
        }
    }

    @Test
    void validatesBoundariesAndDoesNotAcceptUserTokensAsServiceCredentials() {
        String admin = login("demo_admin");
        long roomId = firstRoom();
        assertThat(request(HttpMethod.GET, "/api/v1/rooms?limit=101", null, admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.GET, "/api/v1/rooms?buildingId=-1", null, admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.PATCH, "/api/v1/rooms/" + roomId, Map.of(), admin).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.PATCH, "/api/v1/rooms/" + roomId, Map.of("type", "UNKNOWN"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(internalRequest(HttpMethod.GET, "/internal/v1/settings", null, admin).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(internalRequest(HttpMethod.POST, "/internal/v1/checkin-codes/verify",
                Map.of("roomId", roomId, "code", "AAAAAAAA"), CLASSROOM_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(internalRequest(HttpMethod.POST, "/internal/v1/checkin-codes/verify",
                Map.of("roomId", roomId, "code", "bad"), RESERVATION_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private long createBuilding(String admin) {
        var response = request(HttpMethod.POST, "/api/v1/buildings",
                Map.of("code", "TEST_" + UUID.randomUUID().toString().replace("-", ""), "name", "Test building"), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) data(response).get("id")).longValue();
    }

    private long createRoom(String admin, long buildingId) {
        var response = request(HttpMethod.POST, "/api/v1/rooms", Map.of("buildingId", buildingId,
                "code", "R01", "name", "Test room", "type", "STUDY_ROOM", "enabled", true), admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) data(response).get("id")).longValue();
    }

    private long firstRoom() {
        return jdbc.queryForObject("SELECT MIN(id) FROM platform.room", Long.class);
    }

    private void deleteTestSpaces(long buildingId, Long roomId) {
        if (roomId != null) {
            jdbc.update("DELETE FROM platform.room_checkin_code WHERE room_id = ?", roomId);
            jdbc.update("DELETE FROM platform.seat WHERE room_id = ?", roomId);
            jdbc.update("DELETE FROM platform.room WHERE id = ?", roomId);
        }
        jdbc.update("DELETE FROM platform.building WHERE id = ?", buildingId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        return (List<Map<String, Object>>) response.getBody().get("data");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        AdjustableClock testClock() {
            return new AdjustableClock();
        }
    }

    static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-23T12:00:00Z"));

        void set(Instant value) {
            now.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
