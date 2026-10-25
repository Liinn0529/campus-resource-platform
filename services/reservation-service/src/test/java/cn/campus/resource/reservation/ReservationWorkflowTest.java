package cn.campus.resource.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ReservationWorkflowTest extends ReservationApiTestSupport {
    private static final String STUDENT = "demo_student_01";
    private static final String OTHER = "demo_student_02";

    @BeforeEach
    void clearExistingWorkflowRows() { clearWorkflowRows(); }

    @AfterEach
    void cleanWorkflowRowsAndResetTime() {
        clearWorkflowRows();
        testClock.set(INITIAL_TIME);
    }

    @Test
    void createsAnAtomicReservationLedgerAndEventThenRejectsOverlapButAllowsAdjacency() {
        String token = login(STUDENT);
        long room = roomId("STUDY_ROOM");
        long seat = seatId(room, true);
        var first = create(room, seat, 2, 4, token);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = number(data(first), "id");
        assertThat(first.getHeaders().getLocation()).hasToString("/api/v1/reservations/" + id);
        assertThat(data(first)).containsEntry("status", "CREATED");
        assertThat(jdbc.queryForObject("SELECT user_id FROM reservation.seat_reservation WHERE id = ?",
                Long.class, id)).isEqualTo(platformUserId(STUDENT));
        assertThat(jdbc.queryForObject("SELECT source_module || ':' || source_type || ':' || status "
                + "FROM reservation.seat_occupancy WHERE source_ref_id = ?", String.class, Long.toString(id)))
                .isEqualTo("M1:RESERVATION:ACTIVE");
        assertThat(jdbc.queryForObject("SELECT request_id::text FROM reservation.reservation_event "
                + "WHERE reservation_id = ?", String.class, id))
                .isEqualTo(first.getHeaders().getFirst("X-Request-ID"));
        assertStatus(create(room, seat, 3, 5, token), HttpStatus.CONFLICT);
        assertStatus(create(room, seat, 4, 5, token), HttpStatus.CREATED);
        assertThat(count("seat_reservation")).isEqualTo(2);
        assertThat(count("seat_occupancy")).isEqualTo(2);
        assertThat(count("reservation_event")).isEqualTo(2);
    }

    @Test
    void firstEverConcurrentRequestsForOneSeatCreateExactlyOneGateAndBooking() throws Exception {
        String token = login(STUDENT);
        long room = roomId("STUDY_ROOM");
        long seat = seatId(room, true);
        int attempts = 6;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = new ArrayList<>();
        try (var workers = Executors.newFixedThreadPool(attempts)) {
            var results = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < attempts; i++) {
                results.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Test start timed out"); }
                    return create(room, seat, 2, 3, token).getStatusCode().value();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var result : results) { statuses.add(result.get(30, TimeUnit.SECONDS)); }
        }
        assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(status -> status == 409).count()).isEqualTo(attempts - 1);
        for (String table : List.of("room_gate", "seat_reservation", "seat_occupancy", "reservation_event")) {
            assertThat(count(table)).as(table).isEqualTo(1);
        }
    }

    @Test
    void differentSeatsInTheSameRoomRemainIndependentlyBookable() throws Exception {
        String first = login(STUDENT);
        String second = login(OTHER);
        long room = roomId("STUDY_ROOM");
        List<Long> seats = seatIds(room);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> create(room, seats.get(0), 2, 3, first).getStatusCode().value());
            var right = workers.submit(() -> create(room, seats.get(1), 2, 3, second).getStatusCode().value());
            assertThat(List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS)))
                    .containsExactly(201, 201);
        }
        assertThat(count("room_gate")).isEqualTo(1);
        assertThat(count("seat_reservation")).isEqualTo(2);
    }

    @Test
    void filtersRealPowerOutletsAndReturnsBothEnabledRoomTypes() {
        String token = login(STUDENT);
        var rooms = request(HttpMethod.GET, "/api/v1/reservation/rooms?limit=100", null, token);
        assertStatus(rooms, HttpStatus.OK);
        assertThat(rows(rooms)).extracting(row -> row.get("type"))
                .contains("STUDY_ROOM", "CLASSROOM");
        long room = roomId("CLASSROOM");
        for (boolean outlet : List.of(true, false)) {
            var response = request(HttpMethod.GET, "/api/v1/reservation/rooms/" + room
                    + "/seats?hasPowerOutlet=" + outlet, null, token);
            assertStatus(response, HttpStatus.OK);
            assertThat(rows(response)).hasSize(5).allMatch(row -> row.get("hasPowerOutlet").equals(outlet));
        }
        assertStatus(create(room, seatId(room, true), 2, 3, token), HttpStatus.CREATED);
    }

    @Test
    void classroomAndOpenEndedMaintenanceLedgerRowsBlockAllRoomSeatsWithHalfOpenBoundaries() {
        String token = login(STUDENT);
        long room = roomId("CLASSROOM");
        List<Long> seats = seatIds(room);
        insertExternalOccupancy(room, "M2", "CLASSROOM_BOOKING", "test-classroom-block", 2, 4);
        var unavailable = availability(room, 2, 3, token);
        assertStatus(unavailable, HttpStatus.OK);
        assertThat(availabilitySeats(unavailable)).hasSize(10).allMatch(row -> row.get("available").equals(false));
        assertThat(availabilitySeats(unavailable)).allSatisfy(row ->
                assertThat(row.keySet()).containsExactlyInAnyOrder("id", "code", "hasPowerOutlet", "available"));
        assertStatus(create(room, seats.get(0), 2, 3, token), HttpStatus.CONFLICT);
        assertStatus(create(room, seats.get(0), 4, 5, token), HttpStatus.CREATED);
        releaseExternalOccupancy("test-classroom-block");
        insertExternalOccupancy(room, "M3", "MAINTENANCE", "test-maintenance-block", 1, null);
        assertThat(availabilitySeats(availability(room, 6, 7, token)))
                .allMatch(row -> row.get("available").equals(false));
        assertStatus(create(room, seats.get(1), 6, 7, token), HttpStatus.CONFLICT);
        releaseExternalOccupancy("test-maintenance-block");
        assertStatus(create(room, seats.get(1), 6, 7, token), HttpStatus.CREATED);
    }

    @Test
    void cancellationIsOwnerOnlyIdempotentAndRetainsTheReleasedLedgerRow() {
        String token = login(STUDENT);
        long room = roomId("STUDY_ROOM");
        long seat = seatId(room, true);
        long id = createId(room, seat, 2, 3, token);
        assertStatus(cancel(id, login(OTHER)), HttpStatus.NOT_FOUND);
        assertStatus(cancel(id, login("demo_admin")), HttpStatus.NOT_FOUND);
        var cancelled = cancel(id, token);
        assertStatus(cancelled, HttpStatus.OK);
        assertThat(data(cancelled)).containsEntry("status", "CANCELLED");
        assertStatus(cancel(id, token), HttpStatus.OK);
        assertThat(count("seat_occupancy")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM reservation.seat_occupancy WHERE source_ref_id = ?",
                String.class, Long.toString(id))).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT released_at IS NOT NULL FROM reservation.seat_occupancy "
                + "WHERE source_ref_id = ?", Boolean.class, Long.toString(id))).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation.reservation_event WHERE reservation_id = ? "
                + "AND action = 'CANCELLED'", Integer.class, id)).isEqualTo(1);
        assertStatus(create(room, seat, 2, 3, token), HttpStatus.CREATED);
    }

    @Test
    void cancellationRejectsTheStartBoundaryAndNonCreatedStateWithoutReleasingOccupancy() {
        String token = login(STUDENT);
        long room = roomId("STUDY_ROOM");
        long id = createId(room, seatId(room, true), 2, 3, token);
        testClock.set(INITIAL_TIME.plusSeconds(2 * 3600));
        assertStatus(cancel(id, token), HttpStatus.CONFLICT);
        testClock.set(INITIAL_TIME);
        jdbc.update("UPDATE reservation.seat_reservation SET status = 'CHECKED_IN' WHERE id = ?", id);
        assertStatus(cancel(id, token), HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT status FROM reservation.seat_occupancy WHERE source_ref_id = ?",
                String.class, Long.toString(id))).isEqualTo("ACTIVE");
        assertThat(count("reservation_event")).isEqualTo(1);
    }

    @Test
    void failedReleaseRollsBackTheReservationCancellationAndItsEvent() {
        String token = login(STUDENT);
        long room = roomId("STUDY_ROOM");
        long id = createId(room, seatId(room, true), 2, 3, token);
        jdbc.update("DELETE FROM reservation.seat_occupancy WHERE source_ref_id = ?", Long.toString(id));
        assertStatus(cancel(id, token), HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(jdbc.queryForObject("SELECT status FROM reservation.seat_reservation WHERE id = ?",
                String.class, id)).isEqualTo("CREATED");
        assertThat(count("reservation_event")).isEqualTo(1);
    }

    @Test
    void currentHistoryAndDetailsAlwaysRemainWithinTheAuthenticatedUser() {
        String token = login(STUDENT);
        String other = login(OTHER);
        String admin = login("demo_admin");
        long room = roomId("STUDY_ROOM");
        List<Long> seats = seatIds(room);
        long mine = createId(room, seats.get(0), 2, 3, token);
        long theirs = createId(room, seats.get(1), 2, 3, other);
        assertThat(rows(request(HttpMethod.GET, "/api/v1/reservations", null, token)))
                .extracting(row -> number(row, "id")).containsExactly(mine);
        assertStatus(request(HttpMethod.GET, "/api/v1/reservations/" + theirs, null, token), HttpStatus.NOT_FOUND);
        assertStatus(request(HttpMethod.GET, "/api/v1/reservations/" + mine, null, admin), HttpStatus.NOT_FOUND);
        testClock.set(INITIAL_TIME.plusSeconds(4 * 3600));
        assertThat(rows(request(HttpMethod.GET, "/api/v1/reservations", null, token))).isEmpty();
        assertThat(rows(request(HttpMethod.GET, "/api/v1/reservations?view=history", null, token)))
                .extracting(row -> number(row, "id")).containsExactly(mine);
        assertThat(rows(request(HttpMethod.GET, "/api/v1/reservations?view=history", null, other)))
                .extracting(row -> number(row, "id")).containsExactly(theirs);
        assertThat(rows(request(HttpMethod.GET, "/api/v1/reservations?view=history", null, admin))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM reservation.seat_reservation WHERE id = ?",
                String.class, mine)).isEqualTo("CREATED");
    }

    @Test
    void rebookingHistoryCreatesANewSourceAndRechecksCurrentConflicts() {
        String token = login(STUDENT);
        long room = roomId("STUDY_ROOM");
        long original = createId(room, seatId(room, true), 2, 3, token);
        assertStatus(rebook(original, 5, 6, token), HttpStatus.CONFLICT);
        assertStatus(cancel(original, token), HttpStatus.OK);
        var repeated = rebook(original, 5, 6, token);
        assertStatus(repeated, HttpStatus.CREATED);
        long replacement = number(data(repeated), "id");
        assertThat(replacement).isNotEqualTo(original);
        assertThat(number(data(repeated), "rebookOf")).isEqualTo(original);
        assertStatus(rebook(original, 5, 6, token), HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT source_ref_id FROM reservation.seat_occupancy WHERE status = 'ACTIVE'",
                String.class)).isEqualTo(Long.toString(replacement));
        testClock.set(INITIAL_TIME.plusSeconds(7 * 3600));
        assertStatus(rebook(replacement, 9, 10, token), HttpStatus.CREATED);
        assertThat(count("seat_reservation")).isEqualTo(3);
        assertStatus(rebook(original, 11, 12, login(OTHER)), HttpStatus.NOT_FOUND);
    }

    @Test
    void validatesPastWholeHourOrderingDurationAndUntrustedOwnerFieldsBeforeWriting() {
        String token = login(STUDENT);
        long room = roomId("STUDY_ROOM");
        long seat = seatId(room, true);
        for (Map<String, Object> input : List.of(
                input(room, seat, at(-1), at(1)),
                input(room, seat, at(2).plusMinutes(30), at(3)),
                input(room, seat, at(3), at(2)),
                input(room, seat, at(2), at(7)))) {
            assertStatus(request(HttpMethod.POST, "/api/v1/reservations", input, token), HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var impersonation = new java.util.HashMap<>(input(room, seat, at(2), at(3)));
        impersonation.put("userId", platformUserId(OTHER));
        assertStatus(request(HttpMethod.POST, "/api/v1/reservations", impersonation, token), HttpStatus.BAD_REQUEST);
        assertThat(count("seat_reservation")).isZero();
        assertThat(count("seat_occupancy")).isZero();
        assertStatus(create(room, seat, 2, 6, token), HttpStatus.CREATED);
    }

    @Test
    void latestParametersAndDisabledResourcesAreRecheckedForCreateAndRebook() {
        String admin = login("demo_admin");
        String token = login(STUDENT);
        long room = roomId("STUDY_ROOM");
        long seat = seatId(room, true);
        long original = createId(room, seat, 2, 6, token);
        int hours = platformJdbc.queryForObject("SELECT value FROM platform.system_setting "
                + "WHERE key = 'max_reservation_hours'", Integer.class);
        try {
            assertStatus(platformRequest(HttpMethod.PUT, "/api/v1/settings", Map.of("maxReservationHours", 2), admin),
                    HttpStatus.OK);
            assertStatus(create(room, seat, 7, 10, token), HttpStatus.UNPROCESSABLE_ENTITY);
            assertStatus(cancel(original, token), HttpStatus.OK);
            assertStatus(rebook(original, 7, 10, token), HttpStatus.UNPROCESSABLE_ENTITY);
            assertStatus(platformRequest(HttpMethod.PATCH, "/api/v1/seats/" + seat, Map.of("enabled", false), admin),
                    HttpStatus.OK);
            assertStatus(rebook(original, 7, 8, token), HttpStatus.UNPROCESSABLE_ENTITY);
            assertStatus(create(room, seat, 7, 8, token), HttpStatus.UNPROCESSABLE_ENTITY);
        } finally {
            platformRequest(HttpMethod.PATCH, "/api/v1/seats/" + seat, Map.of("enabled", true), admin);
            platformRequest(HttpMethod.PUT, "/api/v1/settings", Map.of("maxReservationHours", hours), admin);
        }
    }

    @Test
    void remoteIdentityChangesAndMissingCredentialsFailClosed() {
        long room = roomId("STUDY_ROOM");
        long seat = seatId(room, true);
        assertStatus(create(room, seat, 2, 3, null), HttpStatus.UNAUTHORIZED);
        assertStatus(create(room, seat, 2, 3, "test-only-invalid-jwt"), HttpStatus.UNAUTHORIZED);
        assertStatus(create(room, seat, 2, 3, login("demo_property_staff")), HttpStatus.FORBIDDEN);
        String admin = login("demo_admin");
        String token = login(STUDENT);
        long user = platformUserId(STUDENT);
        long version = platformJdbc.queryForObject("SELECT auth_version FROM platform.\"user\" WHERE id = ?", Long.class, user);
        try {
            assertStatus(platformRequest(HttpMethod.PUT, "/api/v1/users/" + user + "/roles",
                    Map.of("roleCodes", Set.of()), admin), HttpStatus.OK);
            assertStatus(create(room, seat, 2, 3, token), HttpStatus.FORBIDDEN);
            assertStatus(platformRequest(HttpMethod.PUT, "/api/v1/users/" + user + "/roles",
                    Map.of("roleCodes", Set.of("STUDENT")), admin), HttpStatus.OK);
            assertStatus(platformRequest(HttpMethod.PATCH, "/api/v1/users/" + user, Map.of("enabled", false), admin),
                    HttpStatus.OK);
            assertStatus(create(room, seat, 2, 3, token), HttpStatus.UNAUTHORIZED);
        } finally {
            platformRequest(HttpMethod.PATCH, "/api/v1/users/" + user, Map.of("enabled", true), admin);
            platformRequest(HttpMethod.PUT, "/api/v1/users/" + user + "/roles",
                    Map.of("roleCodes", Set.of("STUDENT")), admin);
            platformJdbc.update("UPDATE platform.\"user\" SET auth_version = ? WHERE id = ?", version, user);
        }
        assertThat(count("seat_reservation")).isZero();
    }

    private void clearWorkflowRows() {
        jdbc.update("DELETE FROM reservation.reservation_event");
        jdbc.update("DELETE FROM reservation.seat_occupancy");
        jdbc.update("DELETE FROM reservation.seat_reservation");
        jdbc.update("DELETE FROM reservation.room_gate");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM reservation." + table, Integer.class);
    }

    private long roomId(String type) {
        return platformJdbc.queryForObject("SELECT id FROM platform.room WHERE type = ? AND enabled ORDER BY id LIMIT 1",
                Long.class, type);
    }

    private List<Long> seatIds(long room) {
        return platformJdbc.queryForList("SELECT id FROM platform.seat WHERE room_id = ? AND enabled ORDER BY id",
                Long.class, room);
    }

    private long seatId(long room, boolean outlet) {
        return platformJdbc.queryForObject("SELECT id FROM platform.seat WHERE room_id = ? AND enabled "
                + "AND has_power_outlet = ? ORDER BY id LIMIT 1", Long.class, room, outlet);
    }

    private ResponseEntity<Map> create(long room, long seat, int start, int end, String token) {
        return request(HttpMethod.POST, "/api/v1/reservations", input(room, seat, at(start), at(end)), token);
    }

    private long createId(long room, long seat, int start, int end, String token) {
        var response = create(room, seat, start, end, token);
        assertStatus(response, HttpStatus.CREATED);
        return number(data(response), "id");
    }

    private ResponseEntity<Map> cancel(long id, String token) {
        return request(HttpMethod.POST, "/api/v1/reservations/" + id + "/cancel", null, token);
    }

    private ResponseEntity<Map> rebook(long id, int start, int end, String token) {
        return request(HttpMethod.POST, "/api/v1/reservations/" + id + "/rebook",
                Map.of("startAt", at(start).toString(), "endAt", at(end).toString()), token);
    }

    private ResponseEntity<Map> availability(long room, int start, int end, String token) {
        return request(HttpMethod.GET, "/api/v1/reservation/rooms/" + room + "/availability?startAt="
                + encode(at(start).toString()) + "&endAt=" + encode(at(end).toString()), null, token);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> availabilitySeats(ResponseEntity<Map> response) {
        assertStatus(response, HttpStatus.OK);
        return (List<Map<String, Object>>) data(response).get("seats");
    }

    private Map<String, Object> input(long room, long seat, OffsetDateTime start, OffsetDateTime end) {
        return Map.of("roomId", room, "seatId", seat, "startAt", start.toString(), "endAt", end.toString());
    }

    private OffsetDateTime at(int hours) {
        return OffsetDateTime.ofInstant(INITIAL_TIME.plusSeconds(hours * 3600L), ZoneOffset.ofHours(8));
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private long number(Map<String, Object> value, String key) { return ((Number) value.get(key)).longValue(); }

    private void assertStatus(ResponseEntity<?> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
    }

    private void insertExternalOccupancy(long room, String module, String type, String reference, int start, Integer end) {
        jdbc.update("INSERT INTO reservation.seat_occupancy (room_id, seat_id, start_at, end_at, source_module, "
                + "source_type, source_ref_id, status, created_at) VALUES (?, NULL, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
                room, Timestamp.from(at(start).toInstant()), end == null ? null : Timestamp.from(at(end).toInstant()),
                module, type, reference, Timestamp.from(INITIAL_TIME));
    }

    private void releaseExternalOccupancy(String reference) {
        jdbc.update("UPDATE reservation.seat_occupancy SET status = 'RELEASED', released_at = ? WHERE source_ref_id = ?",
                Timestamp.from(INITIAL_TIME), reference);
    }
}
