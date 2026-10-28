package cn.campus.resource.classroom;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ClassroomWorkflowTest extends ClassroomApiTestSupport {
    private static final String STUDENT = "demo_student_01";
    private static final String OTHER = "demo_student_02";
    private static final String ROOM_ADMIN = "demo_room_admin";
    private static final Instant START = INITIAL_TIME.plusSeconds(48 * 3600);
    @Autowired private ClassroomService classroom;
    private String admin;

    @BeforeEach
    void prepareRealApprovalIdentitiesAndAnEmptyWorkflow() {
        clearClassroomRows();
        admin = login("demo_admin");
        roles(STUDENT, List.of("STUDENT"));
        roles(OTHER, List.of("STUDENT"));
        roles(ROOM_ADMIN, List.of("ROOM_ADMIN"));
        roles(DEPARTMENT_USERNAME, List.of("DEPARTMENT_ADMIN"));
        platformJdbc.update("UPDATE platform.room SET enabled=TRUE");
        scope(ROOM_ADMIN, List.of(room(0)));
        scope(DEPARTMENT_USERNAME, List.of(room(0)));
    }

    @AfterEach
    void cleanIsolatedWorkflow() {
        clearClassroomRows();
        testClock.set(INITIAL_TIME);
        platformJdbc.update("UPDATE platform.room SET enabled=TRUE");
    }

    @Test
    void submissionCreatesTheExactTwoStageChainAndActualEventWithOwnerVisibility() {
        String owner = login(STUDENT);
        long id = submit(owner, room(0), START);
        var detail = request(HttpMethod.GET, "/api/v1/classroom-requests/" + id, null, owner);
        status(detail, HttpStatus.OK);
        Map<String, Object> body = data(detail);
        assertThat(application(body)).containsEntry("status", "SUBMITTED");
        assertThat(((Number) application(body).get("applicantId")).longValue()).isEqualTo(platformUserId(STUDENT));
        assertThat((List<?>) body.get("steps")).hasSize(2);
        assertThat(jdbc.queryForList("SELECT step_number,required_role,status FROM classroom.approval_step "
                + "WHERE request_id=? ORDER BY step_number", id))
                .containsExactly(Map.of("step_number", 1, "required_role", "ROOM_ADMIN", "status", "PENDING"),
                        Map.of("step_number", 2, "required_role", "DEPARTMENT_ADMIN", "status", "WAITING"));
        assertThat(count("classroom_request")).isEqualTo(1);
        assertThat(count("classroom_event")).isEqualTo(1);
        assertThat(count("approval_action")).isZero();
        assertThat(rows(request(HttpMethod.GET, "/api/v1/classroom-requests", null, owner))).hasSize(1);
        assertThat(rows(request(HttpMethod.GET, "/api/v1/classroom-requests", null, login(OTHER)))).isEmpty();
        status(request(HttpMethod.GET, "/api/v1/classroom-requests/" + id, null, login(OTHER)), HttpStatus.NOT_FOUND);
        status(request(HttpMethod.GET, "/api/v1/classroom-requests/" + id, null, admin), HttpStatus.NOT_FOUND);
    }

    @Test
    void submissionRequiresAStudentAndRealEnabledClassroomAndValidOffsetInterval() {
        String owner = login(STUDENT);
        status(request(HttpMethod.POST, "/api/v1/classroom-requests", payload(room(0), START), null), HttpStatus.UNAUTHORIZED);
        status(request(HttpMethod.POST, "/api/v1/classroom-requests", payload(room(0), START), admin), HttpStatus.FORBIDDEN);
        long study = platformJdbc.queryForObject("SELECT id FROM platform.room WHERE type='STUDY_ROOM' ORDER BY id LIMIT 1", Long.class);
        status(request(HttpMethod.POST, "/api/v1/classroom-requests", payload(study, START), owner), HttpStatus.UNPROCESSABLE_ENTITY);
        platformJdbc.update("UPDATE platform.room SET enabled=FALSE WHERE id=?", room(0));
        status(request(HttpMethod.POST, "/api/v1/classroom-requests", payload(room(0), START), owner), HttpStatus.UNPROCESSABLE_ENTITY);
        platformJdbc.update("UPDATE platform.room SET enabled=TRUE WHERE id=?", room(0));
        status(request(HttpMethod.POST, "/api/v1/classroom-requests", payload(room(0), INITIAL_TIME.minusSeconds(1)), owner), HttpStatus.UNPROCESSABLE_ENTITY);
        var equal = payload(room(0), START);
        equal.put("endAt", equal.get("startAt"));
        status(request(HttpMethod.POST, "/api/v1/classroom-requests", equal, owner), HttpStatus.UNPROCESSABLE_ENTITY);
        var forged = payload(room(0), START);
        forged.put("applicantId", platformUserId(OTHER));
        status(request(HttpMethod.POST, "/api/v1/classroom-requests", forged, owner), HttpStatus.BAD_REQUEST);
        var blank = payload(room(0), START);
        blank.put("purpose", " ");
        status(request(HttpMethod.POST, "/api/v1/classroom-requests", blank, owner), HttpStatus.BAD_REQUEST);
        assertThat(count("classroom_request")).isZero();
        assertThat(count("classroom_event")).isZero();
    }

    @Test
    void requiresTheCurrentRolePermissionAndExplicitScopeRatherThanAnAdminOverride() {
        long id = submit(login(STUDENT), room(0), START);
        String roomAdmin = login(ROOM_ADMIN);
        scope(ROOM_ADMIN, List.of());
        status(action(id, "approve", roomAdmin), HttpStatus.FORBIDDEN);
        status(action(id, "approve", admin), HttpStatus.FORBIDDEN);
        status(action(id, "approve", login(DEPARTMENT_USERNAME)), HttpStatus.FORBIDDEN);
        assertThat(rows(request(HttpMethod.GET, "/api/v1/classroom-requests?view=pending", null, roomAdmin))).isEmpty();
        scope(ROOM_ADMIN, List.of(room(0)));
        assertThat(rows(request(HttpMethod.GET, "/api/v1/classroom-requests?view=pending", null, roomAdmin))).hasSize(1);
        roles(ROOM_ADMIN, List.of("OPERATOR"));
        status(action(id, "approve", roomAdmin), HttpStatus.FORBIDDEN);
        assertThat(state(id)).isEqualTo("SUBMITTED");
        assertThat(count("approval_action")).isZero();
    }

    @Test
    void sequentialApprovalsPersistPendingBeforeRealM1ConfirmsTheRoomIsOccupied() {
        long id = submit(login(STUDENT), room(0), START);
        var first = action(id, "approve", login(ROOM_ADMIN));
        status(first, HttpStatus.OK);
        assertThat(data(first)).containsEntry("status", "APPROVING");
        status(action(id, "approve", login(ROOM_ADMIN)), HttpStatus.FORBIDDEN);
        var second = action(id, "approve", login(DEPARTMENT_USERNAME));
        status(second, HttpStatus.OK);
        assertThat(data(second)).containsEntry("status", "APPROVED_PENDING_SYNC");
        assertThat(state(id)).isEqualTo("APPROVED_PENDING_SYNC");
        assertThat(count("approval_action")).isEqualTo(2);
        classroom.syncDue();
        assertThat(state(id)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT sync_attempts FROM classroom.classroom_request WHERE id=?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sync_error FROM classroom.classroom_request WHERE id=?", String.class, id)).isNull();
        assertThat(reservationJdbc().queryForObject("SELECT count(*) FROM reservation.seat_occupancy WHERE status='ACTIVE'", Integer.class)).isEqualTo(1);
        classroom.runOnce();
        assertThat(jdbc.queryForObject("SELECT sync_attempts FROM classroom.classroom_request WHERE id=?", Integer.class, id)).isEqualTo(1);
        status(action(id, "cancel", login(STUDENT)), HttpStatus.CONFLICT);
        assertThat(state(id)).isEqualTo("APPROVED");
    }

    @Test
    void forbidsOwnApprovalAndTheSameHumanCompletingBothStagesEvenWithBothRoles() {
        long id = submit(login(STUDENT), room(0), START);
        roles(STUDENT, List.of("STUDENT", "ROOM_ADMIN"));
        scope(STUDENT, List.of(room(0)));
        status(action(id, "approve", login(STUDENT)), HttpStatus.FORBIDDEN);
        roles(OTHER, List.of("ROOM_ADMIN", "DEPARTMENT_ADMIN"));
        scope(OTHER, List.of(room(0)));
        String both = login(OTHER);
        status(action(id, "approve", both), HttpStatus.OK);
        status(action(id, "approve", both), HttpStatus.FORBIDDEN);
        assertThat(state(id)).isEqualTo("APPROVING");
        assertThat(count("approval_action")).isEqualTo(1);
    }

    @Test
    void realM1ConflictIsTerminalAndPreservesThePreviouslyApprovedRequest() {
        long first = submit(login(STUDENT), room(0), START);
        long second = submit(login(OTHER), room(0), START);
        for (long id : List.of(first, second)) {
            status(action(id, "approve", login(ROOM_ADMIN)), HttpStatus.OK);
            status(action(id, "approve", login(DEPARTMENT_USERNAME)), HttpStatus.OK);
            classroom.syncDue();
        }
        assertThat(state(first)).isEqualTo("APPROVED");
        assertThat(state(second)).isEqualTo("SYNC_FAILED");
        assertThat(jdbc.queryForObject("SELECT sync_error FROM classroom.classroom_request WHERE id=?", String.class, second)).isEqualTo("M1_HTTP_409");
        classroom.syncDue();
        assertThat(jdbc.queryForObject("SELECT sync_attempts FROM classroom.classroom_request WHERE id=?", Integer.class, second)).isEqualTo(1);
        assertThat(reservationJdbc().queryForObject("SELECT count(*) FROM reservation.seat_occupancy WHERE status='ACTIVE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void departmentScopeIsRequiredAndRevocationAppliesToAnExistingJwt() {
        long id = submit(login(STUDENT), room(0), START);
        status(action(id, "approve", login(ROOM_ADMIN)), HttpStatus.OK);
        String department = login(DEPARTMENT_USERNAME);
        scope(DEPARTMENT_USERNAME, List.of());
        status(action(id, "approve", department), HttpStatus.FORBIDDEN);
        scope(DEPARTMENT_USERNAME, List.of(room(0)));
        roles(DEPARTMENT_USERNAME, List.of("OPERATOR"));
        status(action(id, "approve", department), HttpStatus.FORBIDDEN);
        assertThat(state(id)).isEqualTo("APPROVING");
        assertThat(count("approval_action")).isEqualTo(1);
    }

    @Test
    void rejectionIsAnActualSingleDecisionAndPreventsLaterApprovalOrCancellation() {
        long id = submit(login(STUDENT), room(0), START);
        status(action(id, "reject", login(ROOM_ADMIN)), HttpStatus.OK);
        assertThat(state(id)).isEqualTo("REJECTED");
        assertThat(count("approval_action")).isEqualTo(1);
        assertThat(count("classroom_event")).isEqualTo(2);
        status(action(id, "reject", login(ROOM_ADMIN)), HttpStatus.CONFLICT);
        status(action(id, "approve", login(ROOM_ADMIN)), HttpStatus.CONFLICT);
        status(action(id, "cancel", login(STUDENT)), HttpStatus.CONFLICT);
        assertThat(count("approval_action")).isEqualTo(1);
    }

    @Test
    void onlyTheApplicantCanCancelBeforeSynchronizationAndHistoryIsRetained() {
        String owner = login(STUDENT);
        long submitted = submit(owner, room(0), START);
        status(action(submitted, "cancel", login(OTHER)), HttpStatus.NOT_FOUND);
        status(action(submitted, "cancel", owner), HttpStatus.OK);
        assertThat(state(submitted)).isEqualTo("CANCELLED");
        long approving = submit(owner, room(0), START.plusSeconds(3600));
        status(action(approving, "approve", login(ROOM_ADMIN)), HttpStatus.OK);
        status(action(approving, "cancel", owner), HttpStatus.OK);
        assertThat(state(approving)).isEqualTo("CANCELLED");
        assertThat(count("classroom_request")).isEqualTo(2);
        assertThat(reservationJdbc().queryForObject("SELECT count(*) FROM reservation.seat_occupancy", Integer.class)).isZero();
    }

    @Test
    void stageDeadlineIsPersistedAndTimeoutIsInclusiveIdempotentAndStopsAtTheStart() {
        long id = submit(login(STUDENT), room(0), START);
        assertThat(Instant.parse((String) application(data(request(HttpMethod.GET, "/api/v1/classroom-requests/" + id,
                null, login(STUDENT)))).get("approvalDueAt")))
                .isEqualTo(INITIAL_TIME.plusSeconds(24 * 3600));
        testClock.set(INITIAL_TIME.plusSeconds(24 * 3600).minusNanos(1));
        classroom.scanTimeouts();
        assertThat(state(id)).isEqualTo("SUBMITTED");
        testClock.set(INITIAL_TIME.plusSeconds(24 * 3600));
        classroom.scanTimeouts();
        assertThat(state(id)).isEqualTo("AUTO_CANCELLED");
        long events = count("classroom_event");
        classroom.scanTimeouts();
        assertThat(count("classroom_event")).isEqualTo(events);
        testClock.set(INITIAL_TIME);
        long capped = submit(login(STUDENT), room(0), INITIAL_TIME.plusSeconds(3600));
        testClock.set(INITIAL_TIME.plusSeconds(3600));
        classroom.scanTimeouts();
        assertThat(state(capped)).isEqualTo("AUTO_CANCELLED");
    }

    @Test
    void theSecondStageGetsItsOwnDeadlineAndExpiredDecisionsCannotBeApplied() {
        long id = submit(login(STUDENT), room(0), START);
        testClock.set(INITIAL_TIME.plusSeconds(2 * 3600));
        status(action(id, "approve", login(ROOM_ADMIN)), HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT approval_due_at FROM classroom.classroom_request WHERE id=?",
                java.sql.Timestamp.class, id).toInstant()).isEqualTo(INITIAL_TIME.plusSeconds(26 * 3600));
        testClock.set(INITIAL_TIME.plusSeconds(26 * 3600));
        status(action(id, "approve", login(DEPARTMENT_USERNAME)), HttpStatus.CONFLICT);
        classroom.scanTimeouts();
        assertThat(state(id)).isEqualTo("AUTO_CANCELLED");
        assertThat(count("approval_action")).isEqualTo(1);
    }

    @Test
    void concurrentApproveAndRejectProduceExactlyOneDecisionAndOneTransition() throws Exception {
        long id = submit(login(STUDENT), room(0), START);
        String reviewer = login(ROOM_ADMIN);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var approve = pool.submit(() -> { start.await(); return action(id, "approve", reviewer).getStatusCode(); });
            var reject = pool.submit(() -> { start.await(); return action(id, "reject", reviewer).getStatusCode(); });
            start.countDown();
            var results = List.of(approve.get(15, TimeUnit.SECONDS), reject.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(HttpStatus.OK::equals).count()).isEqualTo(1);
            assertThat(results.stream().filter(value -> !value.equals(HttpStatus.OK))
                    .allMatch(value -> value.equals(HttpStatus.CONFLICT) || value.equals(HttpStatus.FORBIDDEN))).isTrue();
        }
        assertThat(count("approval_action")).isEqualTo(1);
        assertThat(count("classroom_event")).isEqualTo(2);
        assertThat(state(id)).isIn("APPROVING", "REJECTED");
    }

    @Test
    void waitingForTheRequestLockPastTheDeadlineCannotRecordAnApproval() throws Exception {
        long id = submit(login(STUDENT), room(0), START);
        String reviewer = login(ROOM_ADMIN);
        testClock.set(INITIAL_TIME.plusSeconds(24 * 3600).minusSeconds(1));
        try (Connection blocker = classroomConnection(); var pool = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement statement = blocker.prepareStatement("SELECT id FROM classroom.classroom_request WHERE id=? FOR UPDATE")) {
                statement.setLong(1, id);
                statement.executeQuery();
            }
            var response = pool.submit(() -> action(id, "approve", reviewer));
            awaitBlockedRequest();
            testClock.set(INITIAL_TIME.plusSeconds(24 * 3600));
            blocker.commit();
            status(response.get(15, TimeUnit.SECONDS), HttpStatus.CONFLICT);
        }
        classroom.scanTimeouts();
        assertThat(state(id)).isEqualTo("AUTO_CANCELLED");
        assertThat(count("approval_action")).isZero();
    }

    @Test
    void synchronizationNeverCreatesAnOccupancyAfterTheReservedStart() {
        long id = submit(login(STUDENT), room(0), START);
        status(action(id, "approve", login(ROOM_ADMIN)), HttpStatus.OK);
        status(action(id, "approve", login(DEPARTMENT_USERNAME)), HttpStatus.OK);
        testClock.set(START);
        classroom.syncDue();
        assertThat(state(id)).isEqualTo("SYNC_FAILED");
        assertThat(jdbc.queryForObject("SELECT sync_attempts FROM classroom.classroom_request WHERE id=?", Integer.class, id)).isZero();
        assertThat(reservationJdbc().queryForObject("SELECT count(*) FROM reservation.seat_occupancy", Integer.class)).isZero();
    }

    @Test
    void aBrokenSecondStepRollsBackTheFirstDecisionAndActionAtomically() {
        long id = submit(login(STUDENT), room(0), START);
        jdbc.update("DELETE FROM classroom.approval_step WHERE request_id=? AND step_number=2", id);
        status(action(id, "approve", login(ROOM_ADMIN)), HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(state(id)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT status FROM classroom.approval_step WHERE request_id=? AND step_number=1",
                String.class, id)).isEqualTo("PENDING");
        assertThat(count("approval_action")).isZero();
        assertThat(count("classroom_event")).isEqualTo(1);
    }

    private long submit(String token, long room, Instant start) {
        var response = request(HttpMethod.POST, "/api/v1/classroom-requests", payload(room, start), token);
        status(response, HttpStatus.CREATED);
        assertThat(data(response)).containsEntry("status", "SUBMITTED");
        return ((Number) data(response).get("id")).longValue();
    }

    private Map<String, Object> payload(long room, Instant start) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("roomId", room);
        body.put("startAt", OffsetDateTime.ofInstant(start, ZoneOffset.ofHours(8)).toString());
        body.put("endAt", OffsetDateTime.ofInstant(start.plusSeconds(3600), ZoneOffset.ofHours(8)).toString());
        body.put("purpose", "Isolated approval workflow test");
        return body;
    }

    private ResponseEntity<Map> action(long id, String action, String token) {
        return request(HttpMethod.POST, "/api/v1/classroom-requests/" + id + "/" + action,
                action.equals("cancel") ? null : Map.of("reason", "Isolated decision"), token);
    }

    private void roles(String user, List<String> roles) {
        status(platformRequest(HttpMethod.PUT, "/api/v1/users/" + platformUserId(user) + "/roles",
                Map.of("roleCodes", roles), admin), HttpStatus.OK);
    }

    private void scope(String user, List<Long> rooms) {
        status(platformRequest(HttpMethod.PUT, "/api/v1/users/" + platformUserId(user) + "/room-scopes",
                Map.of("roomIds", rooms), admin), HttpStatus.OK);
    }

    private long room(int offset) {
        return platformJdbc.queryForObject("SELECT id FROM platform.room WHERE type='CLASSROOM' ORDER BY id LIMIT 1 OFFSET ?",
                Long.class, offset);
    }

    private String state(long id) {
        return jdbc.queryForObject("SELECT status FROM classroom.classroom_request WHERE id=?", String.class, id);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM classroom." + table, Long.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> application(Map<String, Object> detail) {
        return (Map<String, Object>) detail.get("request");
    }

    private void awaitBlockedRequest() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE usename='classroom_app' "
                    + "AND wait_event_type='Lock'", Integer.class);
            if (blocked != null && blocked > 0) { return; }
            Thread.sleep(10);
        }
        throw new AssertionError("Real request did not wait for the held database row lock");
    }

    private static void status(ResponseEntity<?> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
    }
}
