package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class NotificationApiTest extends ApiTestSupport {
    private final List<String> sourceKeys = new ArrayList<>();

    @AfterEach
    void removeOnlyThisTestsInboxRows() {
        sourceKeys.forEach(key -> jdbc.update(
                "DELETE FROM platform.notification WHERE source_module = 'M1' AND source_ref_id = ?", key));
    }

    @Test
    void onlyReservationServiceCanPersistARealInboxNotification() {
        var input = input("demo_student_01", "CHECKIN_FIRST");
        for (String credential : List.of(CLASSROOM_TOKEN, MAINTENANCE_TOKEN)) {
            assertThat(accept(input, credential).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(accept(input, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(accept(input, login("demo_admin")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        var response = accept(input, RESERVATION_TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var value = data(response);
        assertThat(value.keySet()).containsExactlyInAnyOrder("id", "userId", "sourceModule", "sourceRefId",
                "reservationId", "roomId", "startAt", "kind", "dueAt", "expiresAt", "createdAt");
        assertThat(value).containsEntry("sourceModule", "M1")
                .containsEntry("sourceRefId", input.get("sourceRefId"))
                .containsEntry("kind", "CHECKIN_FIRST");
        assertThat(response.getBody().get("requestId"))
                .isEqualTo(response.getHeaders().getFirst("X-Request-ID"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.notification WHERE source_ref_id = ?",
                Long.class, input.get("sourceRefId"))).isEqualTo(1);
        long id = ((Number) value.get("id")).longValue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.audit_log WHERE actor_user_id IS NULL "
                + "AND source_service = 'reservation-service' AND action = 'NOTIFICATION_ACCEPTED' "
                + "AND target_type = 'NOTIFICATION' AND target_id = ? AND request_id = ?::uuid",
                Long.class, Long.toString(id), response.getBody().get("requestId"))).isEqualTo(1);
    }

    @Test
    void sameSourceReturnsTheSameNotificationAndOneAuditWhileConflictingContentIsRejected() {
        var input = input("demo_student_01", "CHECKIN_SECOND");
        var first = accept(input, RESERVATION_TOKEN);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var repeated = accept(input, RESERVATION_TOKEN);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(repeated)).isEqualTo(data(first));

        var changed = new HashMap<>(input);
        changed.put("roomId", ((Number) input.get("roomId")).longValue() + 1);
        var conflict = accept(changed, RESERVATION_TOKEN);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).containsEntry("code", "NOTIFICATION_SOURCE_CONFLICT");
        String id = data(first).get("id").toString();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.audit_log WHERE "
                + "action = 'NOTIFICATION_ACCEPTED' AND target_id = ?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void concurrentDeliveryRetriesCreateOnlyOneInboxRowAndAudit() throws Exception {
        var input = input("demo_student_01", "CHECKIN_FIRST");
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<ResponseEntity<Map>>> tasks = java.util.stream.IntStream.range(0, 6)
                    .<Callable<ResponseEntity<Map>>>mapToObj(index -> () -> accept(input, RESERVATION_TOKEN))
                    .toList();
            var responses = executor.invokeAll(tasks).stream().map(result -> {
                try { return result.get(); }
                catch (Exception error) { throw new AssertionError(error); }
            }).toList();
            assertThat(responses).filteredOn(response -> response.getStatusCode() == HttpStatus.CREATED)
                    .hasSize(1);
            assertThat(responses).allMatch(response -> response.getStatusCode() == HttpStatus.CREATED
                    || response.getStatusCode() == HttpStatus.OK);
            var ids = responses.stream().map(response -> ((Number) data(response).get("id")).longValue())
                    .distinct().toList();
            assertThat(ids).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.notification WHERE source_ref_id = ?",
                    Long.class, input.get("sourceRefId"))).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.audit_log WHERE "
                    + "action = 'NOTIFICATION_ACCEPTED' AND target_id = ?", Long.class,
                    Long.toString(ids.getFirst()))).isEqualTo(1);
        }
    }

    @Test
    void usersReadOnlyTheirOwnInboxEvenWhenTheyHaveGlobalAdminPermissions() {
        var first = accept(input("demo_student_01", "CHECKIN_FIRST"), RESERVATION_TOKEN);
        var second = accept(input("demo_student_02", "CHECKIN_SECOND"), RESERVATION_TOKEN);
        long firstId = ((Number) data(first).get("id")).longValue();
        long secondId = ((Number) data(second).get("id")).longValue();
        var own = request(HttpMethod.GET, "/api/v1/notifications", null, login("demo_student_01"));
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(own)).contains(firstId).doesNotContain(secondId);
        assertThat(ids(request(HttpMethod.GET, "/api/v1/notifications", null, login("demo_student_02"))))
                .contains(secondId).doesNotContain(firstId);
        assertThat(ids(request(HttpMethod.GET, "/api/v1/notifications", null, login("demo_admin"))))
                .doesNotContain(firstId, secondId);
        assertThat(request(HttpMethod.GET, "/api/v1/notifications", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(HttpMethod.GET, "/api/v1/notifications", null, RESERVATION_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(HttpMethod.GET, "/api/v1/notifications", null, login("demo_room_admin")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(request(HttpMethod.GET, "/api/v1/notifications?limit=1&offset=0", null,
                login("demo_student_01")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unavailableRecipientsAreRejectedButAPersistedReceiptRemainsIdempotentAfterDisabling() {
        var accepted = input("demo_student_02", "CHECKIN_FIRST");
        var first = accept(accepted, RESERVATION_TOKEN);
        long recipient = userId("demo_student_02");
        try {
            jdbc.update("UPDATE platform.\"user\" SET enabled = FALSE WHERE id = ?", recipient);
            var missing = new HashMap<>(input("demo_student_02", "CHECKIN_SECOND"));
            var response = accept(missing, RESERVATION_TOKEN);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).containsEntry("code", "NOTIFICATION_RECIPIENT_UNAVAILABLE");
            missing.put("userId", Long.MAX_VALUE);
            assertThat(accept(missing, RESERVATION_TOKEN).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            var duplicate = accept(accepted, RESERVATION_TOKEN);
            assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(data(duplicate)).isEqualTo(data(first));
        } finally {
            jdbc.update("UPDATE platform.\"user\" SET enabled = TRUE WHERE id = ?", recipient);
        }
    }

    @Test
    void validatesTrustedServicePayloadAndPaginationInsteadOfStoringArbitraryMessages() {
        var input = input("demo_student_01", "CHECKIN_FIRST");
        for (var change : List.of(Map.of("sourceRefId", "bad/source"), Map.of("userId", 0),
                Map.of("kind", "EMAIL"), Map.of("reservationId", -1), Map.of("roomId", 0),
                Map.of("sourceModule", "M3"), Map.of("message", "Arbitrary message"))) {
            var malformed = new HashMap<>(input);
            malformed.putAll(change);
            assertThat(accept(malformed, RESERVATION_TOKEN).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        var window = new HashMap<>(input);
        window.put("expiresAt", window.get("dueAt"));
        var response = accept(window, RESERVATION_TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("code", "NOTIFICATION_INVALID_WINDOW");
        var nanoseconds = new HashMap<>(input);
        for (String field : List.of("startAt", "dueAt", "expiresAt")) {
            nanoseconds.put(field, Instant.parse((String) input.get(field)).plusNanos(1).toString());
        }
        var tooPrecise = accept(nanoseconds, RESERVATION_TOKEN);
        assertThat(tooPrecise.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(tooPrecise.getBody()).containsEntry("code", "NOTIFICATION_INVALID_WINDOW");
        String student = login("demo_student_01");
        for (String query : List.of("limit=101", "limit=0", "offset=-1")) {
            assertThat(request(HttpMethod.GET, "/api/v1/notifications?" + query, null, student).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform.notification WHERE source_ref_id = ?",
                Long.class, input.get("sourceRefId"))).isZero();
    }

    private Map<String, Object> input(String recipient, String kind) {
        String source = "notification-test:" + UUID.randomUUID();
        sourceKeys.add(source);
        Instant start = Instant.parse("2026-11-01T01:00:00Z");
        boolean first = kind.equals("CHECKIN_FIRST");
        return Map.of("sourceRefId", source, "userId", userId(recipient), "reservationId", 73L,
                "roomId", jdbc.queryForObject("SELECT min(id) FROM platform.room", Long.class),
                "startAt", start.toString(), "kind", kind,
                "dueAt", start.plus(first ? -15 : 10, ChronoUnit.MINUTES).toString(),
                "expiresAt", start.plus(first ? 10 : 15, ChronoUnit.MINUTES).toString());
    }

    private ResponseEntity<Map> accept(Map<String, Object> input, String token) {
        return internalRequest(HttpMethod.POST, "/internal/v1/notifications", input, token);
    }

    @SuppressWarnings("unchecked")
    private List<Long> ids(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        var rows = (List<Map<String, Object>>) response.getBody().get("data");
        return rows.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
    }
}
