package cn.campus.resource.classroom;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

// This external M1 response fixture tests M2 synchronization boundaries, not Phase 8 integration.
class SynchronizationBoundaryTest extends ClassroomApiTestSupport {
    @Autowired private ClassroomMapper mapper;
    @Autowired private PlatformClient platform;
    @Autowired private AccessPolicy access;
    @Autowired private PlatformTransactionManager manager;
    @Autowired private RestClient.Builder builder;
    @Autowired private ObjectMapper json;
    private HttpServer fixture;
    private ClassroomService boundaryService;
    private volatile int replyStatus;
    private volatile boolean wrongSource;
    private volatile boolean redirect;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> observedSource = new AtomicReference<>();
    private String owner;
    private String reviewer;
    private String department;
    private long roomId;

    @BeforeEach
    void createAnExplicitTestOnlyRemoteBoundary() throws IOException {
        clearClassroomRows();
        String admin = login("demo_admin");
        for (var entry : Map.of("demo_student_01", List.of("STUDENT"), "demo_room_admin", List.of("ROOM_ADMIN"),
                DEPARTMENT_USERNAME, List.of("DEPARTMENT_ADMIN")).entrySet()) {
            assertThat(platformRequest(HttpMethod.PUT, "/api/v1/users/" + platformUserId(entry.getKey()) + "/roles",
                    Map.of("roleCodes", entry.getValue()), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        roomId = platformJdbc.queryForObject("SELECT id FROM platform.room WHERE type='CLASSROOM' ORDER BY id LIMIT 1", Long.class);
        for (String user : List.of("demo_room_admin", DEPARTMENT_USERNAME)) {
            assertThat(platformRequest(HttpMethod.PUT, "/api/v1/users/" + platformUserId(user) + "/room-scopes",
                    Map.of("roomIds", List.of(roomId)), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        owner = login("demo_student_01");
        reviewer = login("demo_room_admin");
        department = login(DEPARTMENT_USERNAME);
        fixture = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fixture.createContext("/", this::respond);
        fixture.start();
        replyStatus = 201;
        var client = new OccupancyClient(builder.clone(), "http://127.0.0.1:" + fixture.getAddress().getPort(),
                M1_CLASSROOM_TOKEN, CLASSROOM_TOKEN);
        boundaryService = new ClassroomService(mapper, platform, client, access, testClock, manager, 24);
    }

    @AfterEach
    void stopFixtureAndDiscardIsolatedRows() {
        fixture.stop(0);
        clearClassroomRows();
        testClock.set(INITIAL_TIME);
    }

    @Test
    void onlyAnExactReceiptCanConfirmApprovalAndTheSourceRemainsStable() {
        long id = pending();
        boundaryService.syncDue();
        assertThat(state(id)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT occupancy_id FROM classroom.classroom_request WHERE id=?", Long.class, id)).isEqualTo(91);
        assertThat(observedSource.get()).isEqualTo("classroom-request:" + id);
        boundaryService.syncDue();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(reservationJdbc().queryForObject("SELECT count(*) FROM reservation.seat_occupancy", Integer.class)).isZero();
    }

    @Test
    void serverErrorsRetryThreeTimesWithPersistedBackoffWhileClientErrorsAreTerminal() {
        replyStatus = 503;
        long id = pending();
        boundaryService.syncDue();
        assertThat(state(id)).isEqualTo("APPROVED_PENDING_SYNC");
        testClock.set(INITIAL_TIME.plusSeconds(29));
        boundaryService.syncDue();
        assertThat(calls.get()).isEqualTo(1);
        testClock.set(INITIAL_TIME.plusSeconds(30));
        boundaryService.syncDue();
        testClock.set(INITIAL_TIME.plusSeconds(90));
        boundaryService.syncDue();
        assertThat(state(id)).isEqualTo("SYNC_FAILED");
        assertThat(attempts(id)).isEqualTo(3);
        testClock.set(INITIAL_TIME.plusSeconds(1000));
        boundaryService.syncDue();
        assertThat(calls.get()).isEqualTo(3);
        replyStatus = 409;
        long conflict = pending();
        boundaryService.syncDue();
        assertThat(state(conflict)).isEqualTo("SYNC_FAILED");
        assertThat(attempts(conflict)).isEqualTo(1);
        boundaryService.syncDue();
        assertThat(calls.get()).isEqualTo(4);
    }

    @Test
    void wrongReceiptAndRedirectsNeverBecomeApprovedOrForwardCredentials() {
        wrongSource = true;
        long id = pending();
        boundaryService.syncDue();
        assertThat(state(id)).isEqualTo("SYNC_FAILED");
        assertThat(jdbc.queryForObject("SELECT sync_error FROM classroom.classroom_request WHERE id=?", String.class, id))
                .isEqualTo("M1_INVALID_RECEIPT");
        wrongSource = false;
        redirect = true;
        long redirected = pending();
        boundaryService.syncDue();
        assertThat(state(redirected)).isEqualTo("SYNC_FAILED");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void anExpiredClaimRecoversOnceAndAnExhaustedClaimCannotMakeAFourthAttempt() throws Exception {
        long id = pending();
        jdbc.update("UPDATE classroom.classroom_request SET sync_attempts=1,sync_claim_token=?,sync_claimed_until=? WHERE id=?",
                UUID.randomUUID(), java.sql.Timestamp.from(INITIAL_TIME.minusSeconds(1)), id);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> boundaryService.syncDue());
            var second = pool.submit(() -> boundaryService.syncDue());
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        assertThat(state(id)).isEqualTo("APPROVED");
        assertThat(attempts(id)).isEqualTo(2);
        assertThat(calls.get()).isEqualTo(1);
        long exhausted = pending();
        jdbc.update("UPDATE classroom.classroom_request SET sync_attempts=3,sync_claim_token=?,sync_claimed_until=? WHERE id=?",
                UUID.randomUUID(), java.sql.Timestamp.from(INITIAL_TIME.minusSeconds(1)), exhausted);
        boundaryService.syncDue();
        assertThat(state(exhausted)).isEqualTo("SYNC_FAILED");
        assertThat(attempts(exhausted)).isEqualTo(3);
        assertThat(calls.get()).isEqualTo(1);
    }

    private long pending() {
        Instant start = INITIAL_TIME.plusSeconds(48 * 3600);
        var created = request(HttpMethod.POST, "/api/v1/classroom-requests", Map.of("roomId", roomId,
                "startAt", start.toString(), "endAt", start.plusSeconds(3600).toString(), "purpose", "Boundary fixture test"), owner);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = ((Number) data(created).get("id")).longValue();
        for (String actor : List.of(reviewer, department)) {
            assertThat(request(HttpMethod.POST, "/api/v1/classroom-requests/" + id + "/approve", Map.of(), actor)
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        return id;
    }

    private String state(long id) { return jdbc.queryForObject("SELECT status FROM classroom.classroom_request WHERE id=?", String.class, id); }
    private int attempts(long id) { return jdbc.queryForObject("SELECT sync_attempts FROM classroom.classroom_request WHERE id=?", Integer.class, id); }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            calls.incrementAndGet();
            JsonNode body = json.readTree(exchange.getRequestBody());
            observedSource.set(body.path("sourceRefId").asText());
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + M1_CLASSROOM_TOKEN);
            int status = redirect ? 302 : replyStatus;
            if (redirect) { exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + fixture.getAddress().getPort() + "/redirect-target"); }
            Object response = status < 300 ? Map.of("data", Map.of("id", 91, "roomId", body.path("roomId").asLong(),
                    "startAt", body.path("startAt").asText(), "endAt", body.path("endAt").asText(),
                    "sourceModule", wrongSource ? "M3" : "M2", "sourceType", "CLASSROOM_BOOKING",
                    "sourceRefId", body.path("sourceRefId").asText(), "status", "ACTIVE"), "requestId", "fixture-request")
                    : Map.of("code", "FIXTURE_ERROR", "message", "Bounded test failure", "requestId", "fixture-request");
            byte[] bytes = json.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}
