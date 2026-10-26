package cn.campus.resource.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(OutputCaptureExtension.class)
class ReservationLifecycleTest extends ReservationApiTestSupport {
    private static final String STUDENT = "demo_student_01";
    private static final String OTHER = "demo_student_02";
    private static final Instant START = INITIAL_TIME.plusSeconds(2 * 3600);
    @Autowired private LifecycleService lifecycle;

    @BeforeEach
    void clearPreviousLifecycle() { clearLifecycle(); }

    @AfterEach
    void cleanLifecycleAndRestoreClock() {
        clearLifecycle();
        testClock.set(INITIAL_TIME);
    }

    @Test
    void opensCheckinAtMinusFifteenAndMakesRepeatsIdempotentWithoutStoringOrLoggingTheCode(CapturedOutput output) {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        String code = todayCode(room);
        testClock.set(START.minusSeconds(15 * 60));
        var response = checkin(id, code, student);
        status(response, HttpStatus.OK);
        assertThat(data(response)).containsEntry("status", "CHECKED_IN");
        assertThat(data(response)).doesNotContainKeys("code", "checkinCode");
        assertThat(count("checkin")).isEqualTo(1);
        assertThat(count("violation")).isZero();
        assertThat(eventCount(id, "CHECKED_IN")).isEqualTo(1);
        assertThat(occupancy(id)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT checked_in_at FROM reservation.checkin WHERE reservation_id = ?",
                Timestamp.class, id).toInstant()).isEqualTo(testClock.instant());
        assertThat(jdbc.queryForObject("SELECT row_to_json(c)::text FROM reservation.checkin c "
                + "WHERE reservation_id = ?", String.class, id)).doesNotContain(code);
        assertThat(output.getAll()).doesNotContain(code);

        testClock.set(START.plusSeconds(3600));
        status(checkin(id, code, student), HttpStatus.OK);
        assertThat(count("checkin")).isEqualTo(1);
        assertThat(eventCount(id, "CHECKED_IN")).isEqualTo(1);
    }

    @Test
    void enforcesTheExactOpenAndCloseInstantsEvenBeforeTheScannerRuns() {
        String student = login(STUDENT);
        long room = roomId(0);
        long first = create(room, seatId(room, 0), student);
        long second = create(room, seatId(room, 1), student);
        String code = todayCode(room);
        testClock.set(START.minusSeconds(15 * 60).minusNanos(1));
        failure(checkin(first, code, student), HttpStatus.CONFLICT, "CHECKIN_NOT_OPEN");
        assertThat(count("checkin")).isZero();
        testClock.set(START.plusSeconds(15 * 60).minusNanos(1));
        status(checkin(first, code, student), HttpStatus.OK);
        testClock.set(START.plusSeconds(15 * 60));
        failure(checkin(second, code, student), HttpStatus.CONFLICT, "CHECKIN_CLOSED");
        assertThat(state(second)).isEqualTo("CREATED");
        assertThat(count("checkin")).isEqualTo(1);
    }

    @Test
    void validatesTheActualDCodeForTheBookingsRoomAndRejectsWrongValuesWithoutWriting() {
        String student = login(STUDENT);
        long room = roomId(0);
        long otherRoom = roomId(1);
        long first = create(room, seatId(room, 0), student);
        long second = create(otherRoom, seatId(otherRoom, 0), student);
        String code = todayCode(room);
        // This isolated real D database has no code for the other room, so the same input cannot verify it.
        platformJdbc.update("DELETE FROM platform.room_checkin_code WHERE room_id = ?", otherRoom);
        testClock.set(START);
        String incorrect = (code.charAt(0) == 'A' ? "B" : "A") + code.substring(1);
        failure(checkin(first, incorrect, student), HttpStatus.UNPROCESSABLE_ENTITY, "CHECKIN_CODE_INVALID");
        failure(checkin(second, code, student), HttpStatus.UNPROCESSABLE_ENTITY, "CHECKIN_CODE_INVALID");
        assertThat(count("checkin")).isZero();
        assertThat(state(first)).isEqualTo("CREATED");
        assertThat(state(second)).isEqualTo("CREATED");
        assertThat(count("reservation_event")).isEqualTo(2);
        status(checkin(first, code, student), HttpStatus.OK);
    }

    @Test
    void protectsCheckinOwnershipPermissionsAndInputBeforeAnyStateChange() {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        String code = todayCode(room);
        testClock.set(START);
        status(checkin(id, code, null), HttpStatus.UNAUTHORIZED);
        status(checkin(id, code, login("demo_property_staff")), HttpStatus.FORBIDDEN);
        for (String other : List.of(login(OTHER), login("demo_admin"))) {
            failure(checkin(id, code, other), HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND");
        }
        status(checkin(id, "bad-code", student), HttpStatus.BAD_REQUEST);
        status(request(HttpMethod.POST, "/api/v1/reservations/" + id + "/checkin", Map.of(), student),
                HttpStatus.BAD_REQUEST);
        status(request(HttpMethod.POST, "/api/v1/reservations/" + id + "/checkin",
                Map.of("code", code, "userId", platformUserId(OTHER)), student), HttpStatus.BAD_REQUEST);
        assertThat(state(id)).isEqualTo("CREATED");
        assertThat(count("checkin")).isZero();
    }

    @Test
    void concurrentDuplicateCheckinsCreateOneCheckinAndOneEvent() throws Exception {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        String code = todayCode(room);
        testClock.set(START);
        assertThat(concurrently(4, () -> checkin(id, code, student).getStatusCode().value()))
                .containsOnly(200).hasSize(4);
        assertThat(state(id)).isEqualTo("CHECKED_IN");
        assertThat(count("checkin")).isEqualTo(1);
        assertThat(eventCount(id, "CHECKED_IN")).isEqualTo(1);
        assertThat(count("violation")).isZero();
    }

    @Test
    void aCheckinWaitingForTheRoomLockRechecksTheClockAfterCrossingTheDeadline() throws Exception {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        String code = todayCode(room);
        testClock.set(START.plusSeconds(15 * 60).minusSeconds(1));
        try (var workers = Executors.newSingleThreadExecutor(); Connection blocker = jdbc.getDataSource().getConnection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT room_id FROM reservation.room_gate WHERE room_id = ? FOR UPDATE")) {
                lock.setLong(1, room);
                lock.executeQuery().close();
            }
            var waiting = workers.submit(() -> checkin(id, code, student));
            try {
                awaitRoomLockWait();
                testClock.set(START.plusSeconds(15 * 60));
            } finally {
                blocker.rollback();
            }
            failure(waiting.get(15, TimeUnit.SECONDS), HttpStatus.CONFLICT, "CHECKIN_CLOSED");
        }
        assertThat(state(id)).isEqualTo("CREATED");
        assertThat(count("checkin")).isZero();
        lifecycle.runOnce();
        assertThat(state(id)).isEqualTo("NO_SHOW");
        assertThat(count("violation")).isEqualTo(1);
    }

    @Test
    void sendsBothDueRemindersToTheRealOwnerInboxOnceAndRecordsAcceptedReceipts() {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        testClock.set(START.minusSeconds(15 * 60).minusNanos(1));
        lifecycle.runOnce();
        assertThat(inbox(student)).isEmpty();
        testClock.set(START.minusSeconds(15 * 60));
        lifecycle.runOnce();
        lifecycle.runOnce();
        assertThat(inbox(student)).hasSize(1).allSatisfy(row -> {
            assertThat(row).containsEntry("kind", "CHECKIN_FIRST");
            assertThat(number(row, "reservationId")).isEqualTo(id);
        });
        assertThat(inbox(login(OTHER))).isEmpty();
        assertThat(inbox(login("demo_admin"))).isEmpty();
        testClock.set(START.plusSeconds(10 * 60).minusNanos(1));
        lifecycle.runOnce();
        assertThat(inbox(student)).hasSize(1);
        testClock.set(START.plusSeconds(10 * 60));
        lifecycle.runOnce();
        lifecycle.runOnce();
        assertThat(inbox(student)).hasSize(2).extracting(row -> row.get("kind"))
                .containsExactlyInAnyOrder("CHECKIN_FIRST", "CHECKIN_SECOND");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation.reminder WHERE reservation_id = ? "
                + "AND notification_id IS NOT NULL AND accepted_at IS NOT NULL", Integer.class, id)).isEqualTo(2);
        testClock.set(START.plusSeconds(15 * 60));
        lifecycle.runOnce();
        assertThat(state(id)).isEqualTo("NO_SHOW");
        assertThat(inbox(student)).hasSize(2);
    }

    @Test
    void noShowAtTheExactDeadlineReleasesOnlyItsSourceAndKeepsOwnerScopedViolationHistory() {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        testClock.set(START.plusSeconds(15 * 60).minusNanos(1));
        lifecycle.runOnce();
        assertThat(state(id)).isEqualTo("CREATED");
        testClock.set(START.plusSeconds(15 * 60));
        lifecycle.runOnce();
        lifecycle.runOnce();
        assertThat(state(id)).isEqualTo("NO_SHOW");
        assertThat(occupancy(id)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT cancelled_at IS NULL FROM reservation.seat_reservation WHERE id = ?",
                Boolean.class, id)).isTrue();
        assertThat(count("checkin")).isZero();
        assertThat(count("violation")).isEqualTo(1);
        assertThat(eventCount(id, "NO_SHOW")).isEqualTo(1);
        var mine = request(HttpMethod.GET, "/api/v1/violations", null, student);
        status(mine, HttpStatus.OK);
        assertThat(rows(mine)).hasSize(1).allSatisfy(row -> {
            assertThat(row.keySet()).containsExactlyInAnyOrder("id", "reservationId", "reason", "occurredAt");
            assertThat(number(row, "reservationId")).isEqualTo(id);
            assertThat(row).containsEntry("reason", "NO_SHOW");
        });
        assertThat(rows(request(HttpMethod.GET, "/api/v1/violations", null, login(OTHER)))).isEmpty();
        assertThat(rows(request(HttpMethod.GET, "/api/v1/violations", null, login("demo_admin")))).isEmpty();
        status(request(HttpMethod.GET, "/api/v1/violations", null, null), HttpStatus.UNAUTHORIZED);
        status(request(HttpMethod.GET, "/api/v1/violations", null, login("demo_property_staff")), HttpStatus.FORBIDDEN);
        assertThat(rows(request(HttpMethod.GET, "/api/v1/reservations?view=history", null, student)))
                .anySatisfy(row -> assertThat(row).containsEntry("status", "NO_SHOW"));
        var rebook = request(HttpMethod.POST, "/api/v1/reservations/" + id + "/rebook",
                Map.of("startAt", at(3), "endAt", at(4)), student);
        status(rebook, HttpStatus.CREATED);
        assertThat(number(data(rebook), "rebookOf")).isEqualTo(id);
    }

    @Test
    void concurrentReminderWorkersCreateOneRealInboxItemAndOneAcceptedReceipt() throws Exception {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        testClock.set(START.minusSeconds(15 * 60));
        concurrently(4, () -> { lifecycle.runOnce(); return 1; });
        assertThat(count("reminder")).isEqualTo(1);
        assertThat(inbox(student)).hasSize(1);
        long inboxId = number(inbox(student).getFirst(), "id");
        assertThat(jdbc.queryForObject("SELECT notification_id FROM reservation.reminder "
                + "WHERE reservation_id = ?", Long.class, id)).isEqualTo(inboxId);
        assertThat(jdbc.queryForObject("SELECT status FROM reservation.reminder WHERE reservation_id = ?",
                String.class, id)).isEqualTo("ACCEPTED");
    }

    @Test
    void anUnknownDeliveryAcknowledgementRetriesThePersistedSourceAndDReturnsTheSameInboxItem() throws Exception {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        testClock.set(START.minusSeconds(15 * 60));
        try (Connection blocker = platformMigratorConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.execute("LOCK TABLE platform.notification IN ACCESS EXCLUSIVE MODE");
            }
            try {
                lifecycle.runOnce();
                assertThat(jdbc.queryForObject("SELECT status FROM reservation.reminder WHERE reservation_id = ?",
                        String.class, id)).isEqualTo("PENDING");
                assertThat(jdbc.queryForObject("SELECT notification_id IS NULL AND accepted_at IS NULL "
                        + "FROM reservation.reminder WHERE reservation_id = ?", Boolean.class, id)).isTrue();
            } finally {
                blocker.rollback();
            }
        }
        awaitPersistedNotification();
        var original = inbox(student).getFirst();
        long reminderId = jdbc.queryForObject("SELECT id FROM reservation.reminder WHERE reservation_id = ?",
                Long.class, id);
        assertThat(original).containsEntry("sourceRefId", "reservation-reminder:" + reminderId);
        testClock.set(START.minusSeconds(15 * 60).plusSeconds(30));
        lifecycle.runOnce();
        assertThat(inbox(student)).hasSize(1);
        assertThat(number(inbox(student).getFirst(), "id")).isEqualTo(number(original, "id"));
        assertThat(jdbc.queryForObject("SELECT notification_id FROM reservation.reminder WHERE id = ?",
                Long.class, reminderId)).isEqualTo(number(original, "id"));
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM reservation.reminder WHERE id = ?",
                Integer.class, reminderId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM reservation.reminder WHERE id = ?",
                String.class, reminderId)).isEqualTo("ACCEPTED");
    }

    @Test
    void concurrentScannersCannotDuplicateTheViolationReleaseOrEvent() throws Exception {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        testClock.set(START.plusSeconds(15 * 60));
        concurrently(4, () -> { lifecycle.runOnce(); return 1; });
        assertThat(state(id)).isEqualTo("NO_SHOW");
        assertThat(count("violation")).isEqualTo(1);
        assertThat(eventCount(id, "NO_SHOW")).isEqualTo(1);
        assertThat(occupancy(id)).isEqualTo("RELEASED");
        assertThat(inbox(student)).isEmpty();
    }

    @Test
    void aCheckinCompetingWithNoShowAtTheDeadlineCannotCreateBothTerminalResults() throws Exception {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        String code = todayCode(room);
        testClock.set(START.plusSeconds(15 * 60));
        try (var workers = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var check = workers.submit(() -> { start.await(); return checkin(id, code, student); });
            var scan = workers.submit(() -> { start.await(); lifecycle.runOnce(); return 1; });
            start.countDown();
            status(check.get(20, TimeUnit.SECONDS), HttpStatus.CONFLICT);
            scan.get(20, TimeUnit.SECONDS);
        }
        assertThat(state(id)).isEqualTo("NO_SHOW");
        assertThat(count("checkin")).isZero();
        assertThat(count("violation")).isEqualTo(1);
        assertThat(eventCount(id, "NO_SHOW")).isEqualTo(1);
    }

    @Test
    void cancelledAndCheckedInBookingsDoNotProduceLaterRemindersOrViolations() {
        String student = login(STUDENT);
        long room = roomId(0);
        long cancelled = create(room, seatId(room, 0), student);
        long attended = create(room, seatId(room, 1), student);
        String code = todayCode(room);
        status(request(HttpMethod.POST, "/api/v1/reservations/" + cancelled + "/cancel", null, student), HttpStatus.OK);
        testClock.set(START.minusSeconds(15 * 60));
        status(checkin(attended, code, student), HttpStatus.OK);
        lifecycle.runOnce();
        testClock.set(START.plusSeconds(10 * 60));
        lifecycle.runOnce();
        testClock.set(START.plusSeconds(15 * 60));
        lifecycle.runOnce();
        assertThat(state(cancelled)).isEqualTo("CANCELLED");
        assertThat(state(attended)).isEqualTo("CHECKED_IN");
        assertThat(count("violation")).isZero();
        assertThat(inbox(student)).isEmpty();
    }

    @Test
    void recoverySkipsTheExpiredFirstReminderAndProcessesOnlyTheCurrentWindow() {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        testClock.set(START.plusSeconds(12 * 60));
        lifecycle.runOnce();
        assertThat(inbox(student)).hasSize(1).allSatisfy(row ->
                assertThat(row).containsEntry("kind", "CHECKIN_SECOND"));
        testClock.set(START.plusSeconds(60 * 60));
        lifecycle.runOnce();
        assertThat(state(id)).isEqualTo("NO_SHOW");
        assertThat(inbox(student)).hasSize(1);
        assertThat(count("violation")).isEqualTo(1);
    }

    @Test
    void anInconsistentLedgerRollsBackNoShowAndViolationTogether() {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        jdbc.update("DELETE FROM reservation.seat_occupancy WHERE source_ref_id = ?", Long.toString(id));
        testClock.set(START.plusSeconds(15 * 60));
        try { lifecycle.runOnce(); }
        catch (IllegalStateException expected) { /* A direct run may report the failed atomic transition. */ }
        assertThat(state(id)).isEqualTo("CREATED");
        assertThat(count("violation")).isZero();
        assertThat(eventCount(id, "NO_SHOW")).isZero();
        assertThat(count("reservation_event")).isEqualTo(1);
    }

    @Test
    void realDCodeLookupTimeoutFailsClosedWithoutChangingTheReservation() throws Exception {
        String student = login(STUDENT);
        long room = roomId(0);
        long id = create(room, seatId(room, 0), student);
        String code = todayCode(room);
        testClock.set(START);
        try (Connection blocker = platformMigratorConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.execute("LOCK TABLE platform.room_checkin_code IN ACCESS EXCLUSIVE MODE");
            }
            try {
                failure(checkin(id, code, student), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
            } finally {
                blocker.rollback();
            }
        }
        assertThat(state(id)).isEqualTo("CREATED");
        assertThat(occupancy(id)).isEqualTo("ACTIVE");
        assertThat(count("checkin")).isZero();
        assertThat(count("reservation_event")).isEqualTo(1);
    }

    private void clearLifecycle() {
        clearReservationRows();
        platformJdbc.update("DELETE FROM platform.notification");
    }

    private long roomId(int offset) {
        return platformJdbc.queryForObject("SELECT id FROM platform.room WHERE enabled ORDER BY id LIMIT 1 OFFSET ?",
                Long.class, offset);
    }

    private long seatId(long room, int offset) {
        return platformJdbc.queryForObject("SELECT id FROM platform.seat WHERE room_id = ? AND enabled "
                + "ORDER BY id LIMIT 1 OFFSET ?", Long.class, room, offset);
    }

    private long create(long room, long seat, String student) {
        var response = request(HttpMethod.POST, "/api/v1/reservations",
                Map.of("roomId", room, "seatId", seat, "startAt", at(2), "endAt", at(3)), student);
        status(response, HttpStatus.CREATED);
        return number(data(response), "id");
    }

    private String todayCode(long room) {
        // D retains its real clock for JWT/date; M1's injected clock independently drives lifecycle boundaries.
        var response = platformRequest(HttpMethod.POST, "/api/v1/rooms/" + room + "/checkin-code", null,
                login("demo_admin"));
        status(response, HttpStatus.OK);
        return (String) data(response).get("code");
    }

    private ResponseEntity<Map> checkin(long id, String code, String token) {
        return request(HttpMethod.POST, "/api/v1/reservations/" + id + "/checkin", Map.of("code", code), token);
    }

    private List<Map<String, Object>> inbox(String token) {
        var response = platformRequest(HttpMethod.GET, "/api/v1/notifications", null, token);
        status(response, HttpStatus.OK);
        return rows(response);
    }

    private String state(long id) {
        return jdbc.queryForObject("SELECT status FROM reservation.seat_reservation WHERE id = ?", String.class, id);
    }

    private String occupancy(long id) {
        return jdbc.queryForObject("SELECT status FROM reservation.seat_occupancy WHERE source_module = 'M1' "
                + "AND source_type = 'RESERVATION' AND source_ref_id = ?", String.class, Long.toString(id));
    }

    private int eventCount(long id, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM reservation.reservation_event "
                + "WHERE reservation_id = ? AND action = ?", Integer.class, id, action);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM reservation." + table, Integer.class);
    }

    private String at(int hours) {
        return OffsetDateTime.ofInstant(INITIAL_TIME.plusSeconds(hours * 3600L), ZoneOffset.ofHours(8)).toString();
    }

    private long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }

    private void status(ResponseEntity<?> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
    }

    private void failure(ResponseEntity<Map> response, HttpStatus expected, String code) {
        status(response, expected);
        assertThat(response.getBody()).containsEntry("code", code);
    }

    private List<Integer> concurrently(int attempts, java.util.concurrent.Callable<Integer> operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        var results = new ArrayList<Integer>();
        try (var workers = Executors.newFixedThreadPool(attempts)) {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < attempts; i++) {
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Test start timed out"); }
                    return operation.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) { results.add(future.get(30, TimeUnit.SECONDS)); }
        }
        return results;
    }

    private void awaitRoomLockWait() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity "
                    + "WHERE usename = 'reservation_app' AND wait_event_type = 'Lock' "
                    + "AND query ILIKE '%room_gate%'", Integer.class);
            if (blocked != null && blocked > 0) { return; }
            Thread.sleep(10);
        }
        throw new AssertionError("The actual HTTP check-in did not reach the held PostgreSQL room lock");
    }

    private void awaitPersistedNotification() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (platformJdbc.queryForObject("SELECT count(*) FROM platform.notification", Integer.class) == 1) { return; }
            Thread.sleep(10);
        }
        throw new AssertionError("D did not persist the real request after the HTTP acknowledgement timed out");
    }
}
