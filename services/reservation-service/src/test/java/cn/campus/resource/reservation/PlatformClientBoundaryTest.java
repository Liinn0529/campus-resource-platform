package cn.campus.resource.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

class PlatformClientBoundaryTest {
    private static final String SERVICE_TOKEN = "test-only-private-platform-service-credential-2026";
    private static final String USER_TOKEN = "test-only-forwarded-user-jwt";
    private static final String ROOM = """
            {"id":1,"buildingId":2,"code":"R01","name":"Test room","type":"CLASSROOM","enabled":true}
            """.strip();
    private static final String SEAT = """
            {"id":10,"roomId":1,"code":"S01","hasPowerOutlet":true,"enabled":true}
            """.strip();

    private final AtomicReference<Reply> override = new AtomicReference<>();
    private final ConcurrentLinkedQueue<Observed> observed = new ConcurrentLinkedQueue<>();
    private final AtomicInteger redirectRequests = new AtomicInteger();
    private HttpServer server;
    private PlatformClient client;
    private String origin;

    @BeforeEach
    void startAnExternalResponseFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new PlatformClient(RestClient.builder(), origin, SERVICE_TOKEN);
    }

    @AfterEach
    void stopExternalResponseFixture() {
        if (server != null) { server.stop(0); }
    }

    @Test
    void realHttpRequestsKeepUserAndServiceCredentialsSeparateAndHonorCatalogFilters() {
        Actor actor = client.identity(USER_TOKEN);
        assertThat(actor.id()).isEqualTo(7);
        assertThat(actor.permissions()).containsExactlyInAnyOrder("space:view", "reservation:create");
        assertThat(client.rooms(2L, 20, 3)).hasSize(1);
        assertThat(client.room(1).type()).isEqualTo("CLASSROOM");
        assertThat(client.seats(1, true, 10, 4)).hasSize(1).allMatch(PlatformClient.Seat::hasPowerOutlet);
        assertThat(client.seat(1, 10).id()).isEqualTo(10);
        assertThat(client.settings().maxReservationHours()).isEqualTo(4);

        assertThat(observed).hasSize(6);
        assertThat(observed.stream().filter(request -> request.path().equals("/api/v1/me"))
                .allMatch(request -> ("Bearer " + USER_TOKEN).equals(request.authorization()))).isTrue();
        assertThat(observed.stream().filter(request -> request.path().startsWith("/internal/v1/"))
                .allMatch(request -> ("Bearer " + SERVICE_TOKEN).equals(request.authorization()))).isTrue();
        assertThat(observed.stream().anyMatch(request -> request.path().equals("/internal/v1/rooms")
                && "limit=20&offset=3&buildingId=2".equals(request.query()))).isTrue();
        assertThat(observed.stream().anyMatch(request -> request.path().endsWith("/seats")
                && "enabledOnly=true&limit=10&offset=4&hasPowerOutlet=true".equals(request.query()))).isTrue();
        assertThat(observed.stream().anyMatch(request -> request.path().endsWith("/seats")
                && "enabledOnly=true&limit=1&offset=0&seatId=10".equals(request.query()))).isTrue();
    }

    @Test
    void distinguishesAnInvalidUserFromUnavailableOrMisconfiguredPlatformAccess() {
        override.set(new Reply(401, errorBody(), null));
        rejected(() -> client.identity(USER_TOKEN), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        rejected(() -> client.settings(), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");

        for (int status : List.of(403, 500, 503)) {
            override.set(new Reply(status, errorBody(), null));
            rejected(() -> client.identity(USER_TOKEN), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
            rejected(() -> client.room(1), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        }

        override.set(new Reply(404, errorBody(), null));
        rejected(() -> client.room(1), HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    @Test
    void refusesRedirectsAndMalformedSuccessfulResponsesInsteadOfFollowingOrDefaulting() {
        override.set(new Reply(302, envelope("[" + ROOM + "]"), origin + "/redirect-target"));
        rejected(() -> client.rooms(null, 100, 0), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        assertThat(redirectRequests.get()).isZero();

        for (String body : List.of("not-json", "{}", "{\"data\":null,\"requestId\":\"test-request\"}",
                "{\"data\":" + ROOM + "}", "{\"data\":" + ROOM + ",\"requestId\":\"\"}")) {
            override.set(new Reply(200, body, null));
            rejected(() -> client.room(1), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        }
    }

    @Test
    void rejectsIncompleteOrInconsistentIdentityRoomAndSettingData() {
        for (String room : List.of(
                ROOM.replace(",\"enabled\":true", ""),
                ROOM.replace("\"enabled\":true", "\"available\":true"),
                ROOM.replace("\"id\":1", "\"id\":2"),
                ROOM.replace("\"type\":\"CLASSROOM\"", "\"type\":null"),
                ROOM.replace("\"buildingId\":2", "\"buildingId\":0"))) {
            override.set(new Reply(200, envelope(room), null));
            rejected(() -> client.room(1), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        }

        for (String identity : List.of(
                "{\"id\":0,\"username\":\"test-user\",\"roles\":[\"STUDENT\"],\"permissions\":[\"space:view\"]}",
                "{\"id\":7,\"username\":\"test-user\",\"roles\":null,\"permissions\":[\"space:view\"]}",
                "{\"id\":7,\"username\":\"test-user\",\"roles\":[\"STUDENT\"],\"permissions\":[\"invalid permission\"]}")) {
            override.set(new Reply(200, envelope(identity), null));
            rejected(() -> client.identity(USER_TOKEN), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        }
        for (String setting : List.of("{}", "{\"maxReservationHours\":0}", "{\"maxReservationHours\":-1}")) {
            override.set(new Reply(200, envelope(setting), null));
            rejected(() -> client.settings(), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        }
    }

    @Test
    void checksReturnedSeatAndRoomFiltersAndTreatsAMissingSeatAsUnavailable() {
        for (String seat : List.of(
                SEAT.replace("\"roomId\":1", "\"roomId\":2"),
                SEAT.replace("\"enabled\":true", "\"enabled\":false"),
                SEAT.replace("\"hasPowerOutlet\":true", "\"hasPowerOutlet\":false"),
                SEAT.replace(",\"hasPowerOutlet\":true", ""),
                SEAT.replace(",\"enabled\":true", ""))) {
            override.set(new Reply(200, envelope("[" + seat + "]"), null));
            rejected(() -> client.seats(1, true, 100, 0), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        }
        override.set(new Reply(200, envelope("[" + SEAT.replace("\"id\":10", "\"id\":11") + "]"), null));
        rejected(() -> client.seat(1, 10), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        override.set(new Reply(200, envelope("[" + SEAT + "," + SEAT + "]"), null));
        rejected(() -> client.seats(1, null, 1, 0), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        rejected(() -> client.seats(1, null, 100, 0), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");

        override.set(new Reply(200, envelope("[" + ROOM.replace("\"enabled\":true", "\"enabled\":false") + "]"), null));
        rejected(() -> client.rooms(null, 100, 0), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        override.set(new Reply(200, envelope("[" + ROOM + "]"), null));
        rejected(() -> client.rooms(99L, 100, 0), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");

        override.set(new Reply(200, envelope("[]"), null));
        rejected(() -> client.seat(1, 10), HttpStatus.UNPROCESSABLE_ENTITY, "RESOURCE_UNAVAILABLE");
    }

    private void rejected(Executable call, HttpStatus status, String code) {
        ApiFailure failure = assertThrows(ApiFailure.class, call);
        assertThat(failure.status()).isEqualTo(status);
        assertThat(failure.code()).isEqualTo(code);
        assertThat(failure.getMessage().contains(SERVICE_TOKEN)).isFalse();
        assertThat(failure.getMessage().contains(USER_TOKEN)).isFalse();
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            observed.add(new Observed(path, exchange.getRequestURI().getRawQuery(),
                    exchange.getRequestHeaders().getFirst("Authorization")));
            if (path.equals("/redirect-target")) {
                redirectRequests.incrementAndGet();
            }
            Reply response = override.get();
            if (response == null || path.equals("/redirect-target")) { response = validResponse(path); }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (response.location() != null) { exchange.getResponseHeaders().set("Location", response.location()); }
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private Reply validResponse(String path) {
        String data = switch (path) {
            case "/api/v1/me" -> """
                    {"id":7,"username":"test-user","displayName":"Test identity",
                     "roles":["STUDENT"],"permissions":["space:view","reservation:create"]}
                    """;
            case "/internal/v1/rooms", "/redirect-target" -> "[" + ROOM + "]";
            case "/internal/v1/rooms/1" -> ROOM;
            case "/internal/v1/rooms/1/seats" -> "[" + SEAT + "]";
            case "/internal/v1/settings" -> "{\"maxReservationHours\":4}";
            default -> null;
        };
        return data == null ? new Reply(404, errorBody(), null) : new Reply(200, envelope(data), null);
    }

    private String envelope(String data) { return "{\"data\":" + data + ",\"requestId\":\"test-request\"}"; }

    private String errorBody() {
        return "{\"code\":\"TEST_UPSTREAM_ERROR\",\"message\":\"" + SERVICE_TOKEN
                + "\",\"requestId\":\"test-request\"}";
    }

    private record Reply(int status, String body, String location) { }
    private record Observed(String path, String query, String authorization) {
        @Override public String toString() { return "Observed[path=" + path + ",credentials=redacted]"; }
    }
}
