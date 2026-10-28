package cn.campus.resource.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

class AuditDeliveryTest extends ReservationApiTestSupport {
    @Autowired private AuditDeliveryMapper mapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private ObjectMapper json;
    private HttpServer boundary;
    private AuditDeliveryService service;
    private final AtomicInteger replyStatus = new AtomicInteger(503);
    private final ArrayList<String> bodies = new ArrayList<>();
    private volatile String reply = "{\"data\":{\"id\":7,\"status\":\"ACCEPTED\"},\"requestId\":\"audit-fixture\"}";

    @BeforeEach void setupBoundary() throws Exception {
        clearReservationRows();
        boundary = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        boundary.createContext("/internal/v1/audit-events", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + RESERVATION_TOKEN);
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(replyStatus.get(), bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        boundary.start();
        var client = new PlatformClient(RestClient.builder(), "http://127.0.0.1:" + boundary.getAddress().getPort(), RESERVATION_TOKEN);
        service = new AuditDeliveryService(mapper, client, testClock, transactionManager);
        long room = platformJdbc.queryForObject("SELECT min(id) FROM platform.room WHERE enabled", Long.class);
        long seat = platformJdbc.queryForObject("SELECT min(id) FROM platform.seat WHERE room_id=?", Long.class, room);
        var response = request(HttpMethod.POST, "/api/v1/reservations", Map.of("roomId", room, "seatId", seat,
                "startAt", INITIAL_TIME.plusSeconds(3600).toString(), "endAt", INITIAL_TIME.plusSeconds(7200).toString()), login("demo_student_01"));
        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @AfterEach void cleanupBoundary() { if (boundary != null) { boundary.stop(0); } clearReservationRows(); }

    @Test void persistsAtomicObligationAndRetriesAtThirtyThenSixtySecondsWithSameEvent() throws Exception {
        assertThat(row()).containsEntry("status", "PENDING").containsEntry("attempt_count", 0);
        service.dispatchDue();
        assertThat(row()).containsEntry("status", "PENDING").containsEntry("attempt_count", 1);
        testClock.set(INITIAL_TIME.plusSeconds(29)); service.dispatchDue(); assertThat(bodies).hasSize(1);
        testClock.set(INITIAL_TIME.plusSeconds(30)); service.dispatchDue(); assertThat(bodies).hasSize(2);
        testClock.set(INITIAL_TIME.plusSeconds(89)); service.dispatchDue(); assertThat(bodies).hasSize(2);
        testClock.set(INITIAL_TIME.plusSeconds(90)); replyStatus.set(200); service.dispatchDue();
        assertThat(row()).containsEntry("status", "ACCEPTED").containsEntry("attempt_count", 3).containsEntry("audit_id", 7L);
        assertThat(bodies).hasSize(3).allMatch(bodies.getFirst()::equals);
        var sent = json.readTree(bodies.getFirst());
        assertThat(sent.get("sourceModule").asText()).isEqualTo("M1");
        assertThat(sent.get("actorUserId").asLong()).isEqualTo(platformUserId("demo_student_01"));
        assertThat(sent.get("sourceEventId").asText()).startsWith("reservation-event:");
        assertThat(sent.size()).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation.audit_attempt WHERE finished_at IS NOT NULL", Integer.class)).isEqualTo(3);
        service.dispatchDue(); assertThat(bodies).hasSize(3);
    }

    @Test void thirdServerFailureIsTerminalAndNeverMakesFourthAttempt() {
        service.dispatchDue(); testClock.set(INITIAL_TIME.plusSeconds(30)); service.dispatchDue();
        testClock.set(INITIAL_TIME.plusSeconds(90)); service.dispatchDue();
        assertThat(row()).containsEntry("status", "FAILED").containsEntry("attempt_count", 3);
        testClock.set(INITIAL_TIME.plusSeconds(999)); service.dispatchDue(); assertThat(bodies).hasSize(3);
    }

    @Test void acceptsACreatedReceiptAndDoesNotResendAfterAcknowledgement() {
        replyStatus.set(201); service.dispatchDue(); service.dispatchDue();
        assertThat(row()).containsEntry("status", "ACCEPTED").containsEntry("attempt_count", 1);
        assertThat(bodies).hasSize(1);
    }

    @Test void badReceiptIsTerminalAndDoesNotInventAnAuditId() {
        replyStatus.set(200); reply = "{\"data\":{\"id\":7,\"status\":\"UNKNOWN\"},\"requestId\":\"fixture\"}";
        service.dispatchDue(); service.dispatchDue();
        assertThat(row()).containsEntry("status", "FAILED").containsEntry("audit_id", null)
                .containsEntry("last_error_code", "PLATFORM_INVALID_RESPONSE");
        assertThat(bodies).hasSize(1);
    }

    @Test void forbiddenResponseIsTerminal() {
        replyStatus.set(403); service.dispatchDue();
        testClock.set(INITIAL_TIME.plusSeconds(90)); service.dispatchDue();
        assertThat(row()).containsEntry("status", "FAILED").containsEntry("last_error_code", "PLATFORM_HTTP_403");
        assertThat(bodies).hasSize(1);
    }

    @Test void aNetworkFailureKeepsThePersistentEventForABoundedRetry() {
        boundary.stop(0);
        service.dispatchDue();
        assertThat(row()).containsEntry("status", "PENDING").containsEntry("attempt_count", 1)
                .containsEntry("last_error_code", "PLATFORM_NETWORK_ERROR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation.reservation_event", Integer.class)).isEqualTo(1);
    }

    @Test void aRestartRecoversExpiredClaimWithoutLosingItsOriginalAttempt() {
        long id = jdbc.queryForObject("SELECT id FROM reservation.audit_delivery", Long.class);
        jdbc.update("UPDATE reservation.audit_delivery SET status='SENDING',attempt_count=1,claim_token=?,claimed_until=?", UUID.randomUUID(), Timestamp.from(INITIAL_TIME.minusSeconds(1)));
        jdbc.update("INSERT INTO reservation.audit_attempt(delivery_id,attempt_number,started_at) VALUES(?,1,?)", id, Timestamp.from(INITIAL_TIME.minusSeconds(31)));
        replyStatus.set(200); service.dispatchDue();
        assertThat(row()).containsEntry("status", "ACCEPTED").containsEntry("attempt_count", 2);
        assertThat(jdbc.queryForList("SELECT result_code FROM reservation.audit_attempt ORDER BY attempt_number", String.class))
                .containsExactly("DELIVERY_ACK_UNKNOWN", "ACCEPTED");
    }

    @Test void anExpiredThirdClaimBecomesFailedWithoutAnotherHttpCall() {
        long id = jdbc.queryForObject("SELECT id FROM reservation.audit_delivery", Long.class);
        jdbc.update("UPDATE reservation.audit_delivery SET status='SENDING',attempt_count=3,claim_token=?,claimed_until=?", UUID.randomUUID(), Timestamp.from(INITIAL_TIME.minusSeconds(1)));
        jdbc.update("INSERT INTO reservation.audit_attempt(delivery_id,attempt_number,started_at) VALUES(?,3,?)", id, Timestamp.from(INITIAL_TIME.minusSeconds(31)));
        service.dispatchDue();
        assertThat(row()).containsEntry("status", "FAILED").containsEntry("attempt_count", 3);
        assertThat(bodies).isEmpty();
    }

    private Map<String, Object> row() { return jdbc.queryForMap("SELECT status,attempt_count,audit_id,last_error_code FROM reservation.audit_delivery"); }
}
