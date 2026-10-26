package cn.campus.resource.classroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

class PlatformClientBoundaryTest {
    private static final String SERVICE = "test-only-classroom-platform-credential-at-least-32-bytes";
    private static final String USER = "test-only-forwarded-classroom-user-jwt";
    private static final String ROOM = """
            {"id":1,"buildingId":2,"code":"C01","name":"Test classroom","type":"CLASSROOM","enabled":true}
            """;
    private static final String APPROVER = """
            {"id":7,"username":"test-approver","displayName":"Fixture",
             "roles":["ROOM_ADMIN"],"permissions":["classroom:approve"]}
            """;
    private HttpServer server;
    private PlatformClient client;
    private String origin;
    private volatile String identity;
    private volatile String scopes;
    private volatile String room;
    private volatile int identityStatus;
    private volatile int scopeStatus;
    private volatile String redirect;
    private final ConcurrentLinkedQueue<Observed> observed = new ConcurrentLinkedQueue<>();

    @BeforeEach
    void startExternalResponseFixture() throws IOException {
        identity = APPROVER;
        scopes = "[1,3]";
        room = ROOM;
        identityStatus = scopeStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new PlatformClient(RestClient.builder(), origin, SERVICE);
    }

    @AfterEach
    void stopFixture() { server.stop(0); }

    @Test
    void currentIdentityAndOwnScopesUseOnlyTheUserCredentialWhileMetadataUsesTheServiceCredential() {
        var actor = client.identity(USER);
        assertThat(actor.id()).isEqualTo(7);
        assertThat(actor.roomIds()).containsExactlyInAnyOrder(1L, 3L);
        assertThat(client.room(1).enabled()).isTrue();
        assertThat(observed).hasSize(3);
        assertThat(observed.stream().filter(row -> row.path().startsWith("/api/v1/me")))
                .allMatch(row -> row.credential().equals("Bearer " + USER));
        assertThat(observed.stream().filter(row -> row.path().startsWith("/internal/")))
                .allMatch(row -> row.credential().equals("Bearer " + SERVICE));
    }

    @Test
    void aStudentDoesNotAcquireApprovalScopesOrNeedAnExtraScopeRequest() {
        identity = """
                {"id":7,"username":"test-student","roles":["STUDENT"],"permissions":["classroom:apply"]}
                """;
        assertThat(client.identity(USER).roomIds()).isEmpty();
        assertThat(observed).hasSize(1);
    }

    @Test
    void revokedScopesOrInvalidCredentialsFailClosedRatherThanDefaultingToAnEmptyScope() {
        scopeStatus = 403;
        denied(() -> client.identity(USER), HttpStatus.FORBIDDEN, "FORBIDDEN");
        scopeStatus = 401;
        denied(() -> client.identity(USER), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        scopeStatus = 500;
        denied(() -> client.identity(USER), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        identityStatus = 401;
        denied(() -> client.identity(USER), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @Test
    void malformedScopeOrderingIdentityAndRequiredRoomFieldsCannotBecomeAuthorizationData() {
        for (String invalid : List.of("[3,1]", "[1,1]", "[0]", "[null]", "null", "{}")) {
            scopes = invalid;
            denied(() -> client.identity(USER), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        }
        scopes = "[]";
        identity = APPROVER.replace("\"id\":7", "\"id\":0");
        denied(() -> client.identity(USER), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        for (String invalid : List.of(ROOM.replace("\"id\":1", "\"id\":2"),
                ROOM.replace("\"enabled\":true", "\"enabled\":null"),
                ROOM.replace(",\"enabled\":true", ""),
                ROOM.replace("\"type\":\"CLASSROOM\"", "\"type\":null"))) {
            room = invalid;
            denied(() -> client.room(1), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        }
    }

    @Test
    void redirectsDoNotForwardCredentialsToAnotherEndpoint() {
        redirect = origin + "/redirect-target";
        denied(() -> client.room(1), HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE");
        assertThat(observed).hasSize(1).noneMatch(row -> row.path().equals("/redirect-target"));
    }

    private void denied(Executable request, HttpStatus status, String code) {
        var failure = assertThrows(ApiFailure.class, request);
        assertThat(failure.status()).isEqualTo(status);
        assertThat(failure.code()).isEqualTo(code);
        assertThat(failure.getMessage()).doesNotContain(USER, SERVICE);
    }

    private void respond(HttpExchange request) throws IOException {
        try (request) {
            String path = request.getRequestURI().getPath();
            observed.add(new Observed(path, request.getRequestHeaders().getFirst("Authorization")));
            int status = path.equals("/api/v1/me") ? identityStatus
                    : path.equals("/api/v1/me/room-scopes") ? scopeStatus : 200;
            String data = path.equals("/api/v1/me") ? identity
                    : path.equals("/api/v1/me/room-scopes") ? scopes : room;
            if (redirect != null) {
                status = 302;
                request.getResponseHeaders().set("Location", redirect);
            }
            byte[] body = (status == 200 ? "{\"data\":" + data + ",\"requestId\":\"test-request\"}"
                    : "{\"code\":\"FIXTURE_ERROR\",\"message\":\"" + SERVICE + "\",\"requestId\":\"test-request\"}")
                    .getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json");
            request.sendResponseHeaders(status, body.length);
            request.getResponseBody().write(body);
        }
    }

    private record Observed(String path, String credential) {
        @Override public String toString() { return "Observed[path=" + path + ",credential=redacted]"; }
    }
}
