package cn.campus.resource.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cn.campus.resource.testing.DatabaseTestSupport;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClientException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = {Application.class, ReservationApiTestSupport.TimeConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ReservationApiTestSupport {
    protected static final Instant INITIAL_TIME = Instant.parse("2026-11-01T00:00:00Z");
    protected static final String SEED_PASSWORD = DatabaseTestSupport.SEED_PASSWORD;
    protected static final String RESERVATION_TOKEN = "test-only-reservation-service-token-at-least-thirty-two-bytes";
    protected static final String CLASSROOM_TOKEN = "test-only-classroom-service-token-at-least-thirty-two-bytes";
    protected static final String MAINTENANCE_TOKEN = "test-only-maintenance-service-token-at-least-thirty-two-bytes";
    private static final PostgreSQLContainer<?> DATABASE = DatabaseTestSupport.newContainer();
    private static final TestRestTemplate PLATFORM_HTTP = boundedClient();
    private static final Process PLATFORM_PROCESS;
    protected static final String PLATFORM_URL;

    static {
        DATABASE.start();
        try {
            Path repository = repositoryRoot();
            Path jar = repository.resolve("services/platform-service/target/platform-service-0.1.0-SNAPSHOT.jar");
            if (!Files.isRegularFile(jar)) {
                throw new IllegalStateException("Run full-reactor verify so platform-service is packaged before M1 HTTP tests");
            }
            Path log = repository.resolve("services/reservation-service/target/platform-integration.log");
            Files.createDirectories(log.getParent());
            int port;
            try (ServerSocket available = new ServerSocket(0)) { port = available.getLocalPort(); }
            PLATFORM_URL = "http://127.0.0.1:" + port;
            ProcessBuilder process = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString(),
                    "--spring.profiles.active=local", "--server.address=127.0.0.1", "--server.port=" + port)
                    .directory(repository.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
            // Keep inherited application settings from redirecting an integration test to another database.
            process.environment().keySet().removeIf(name -> name.startsWith("SPRING_")
                    || name.startsWith("DB_") || name.startsWith("FLYWAY_")
                    || name.startsWith("CAMPUS_") || name.startsWith("PLATFORM_"));
            process.environment().putAll(Map.ofEntries(
                    Map.entry("DB_URL", DatabaseTestSupport.jdbcUrl(DATABASE, "platform")),
                    Map.entry("DB_USERNAME", "platform_app"),
                    Map.entry("DB_PASSWORD", DatabaseTestSupport.appPassword("platform")),
                    Map.entry("FLYWAY_USERNAME", "platform_migrator"),
                    Map.entry("FLYWAY_PASSWORD", DatabaseTestSupport.migratorPassword("platform")),
                    Map.entry("CAMPUS_SEED_PASSWORD", SEED_PASSWORD),
                    Map.entry("PLATFORM_JWT_SECRET", "test-only-platform-jwt-signing-secret-at-least-thirty-two-bytes"),
                    Map.entry("PLATFORM_RESERVATION_TOKEN", RESERVATION_TOKEN),
                    Map.entry("PLATFORM_CLASSROOM_TOKEN", CLASSROOM_TOKEN),
                    Map.entry("PLATFORM_MAINTENANCE_TOKEN", MAINTENANCE_TOKEN),
                    Map.entry("CAMPUS_BUSINESS_ZONE", "Asia/Shanghai")));
            PLATFORM_PROCESS = process.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> stop(PLATFORM_PROCESS), "platform-integration-stop"));
            awaitPlatform(log);
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry properties) {
        DatabaseTestSupport.register(properties, DATABASE, "reservation");
        properties.add("campus.platform.base-url", () -> PLATFORM_URL);
        properties.add("campus.platform.service-token", () -> RESERVATION_TOKEN);
        properties.add("campus.business-zone", () -> "Asia/Shanghai");
        properties.add("campus.reservation.scheduler-enabled", () -> "false");
        properties.add("campus.reservation.completion-enabled", () -> "true");
    }

    @Autowired protected TestRestTemplate http;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected AdjustableClock testClock;
    protected final JdbcTemplate platformJdbc = new JdbcTemplate(new DriverManagerDataSource(
            DatabaseTestSupport.jdbcUrl(DATABASE, "platform"), "platform_app",
            DatabaseTestSupport.appPassword("platform")));

    @BeforeEach
    void resetReservationClock() { testClock.set(INITIAL_TIME); }

    protected String login(String username) {
        var response = platformRequest(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", username, "password", SEED_PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object token = data(response).get("accessToken");
        assertThat(token instanceof String).isTrue();
        return (String) token;
    }

    protected ResponseEntity<Map> request(HttpMethod method, String path, Object body, String token) {
        return exchange(http, method, path, body, token);
    }

    protected ResponseEntity<Map> platformRequest(HttpMethod method, String path, Object body, String token) {
        return exchange(PLATFORM_HTTP, method, PLATFORM_URL + path, body, token);
    }

    protected void clearReservationRows() {
        for (String table : List.of("audit_attempt", "audit_delivery", "reminder", "checkin", "violation", "reservation_event",
                "seat_occupancy", "seat_reservation", "room_gate")) {
            jdbc.update("DELETE FROM reservation." + table);
        }
    }

    protected Connection platformMigratorConnection() throws SQLException {
        return new DriverManagerDataSource(DatabaseTestSupport.jdbcUrl(DATABASE, "platform"),
                "platform_migrator", DatabaseTestSupport.migratorPassword("platform")).getConnection();
    }

    protected long platformUserId(String username) {
        return platformJdbc.queryForObject("SELECT id FROM platform.\"user\" WHERE username = ?", Long.class, username);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> data(ResponseEntity<Map> response) {
        assertThat(response.getBody() != null).isTrue();
        Object value = response.getBody().get("data");
        assertThat(value instanceof Map).isTrue();
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> rows(ResponseEntity<Map> response) {
        assertThat(response.getBody() != null).isTrue();
        Object value = response.getBody().get("data");
        assertThat(value instanceof List).isTrue();
        return (List<Map<String, Object>>) value;
    }

    private static ResponseEntity<Map> exchange(TestRestTemplate client, HttpMethod method,
            String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) { headers.setBearerAuth(token); }
        URI target = URI.create(path);
        if (!target.isAbsolute()) { target = URI.create(client.getRootUri() + path); }
        return client.exchange(target, method, new HttpEntity<>(body, headers), Map.class);
    }

    private static TestRestTemplate boundedClient() {
        TestRestTemplate client = new TestRestTemplate();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client.getRestTemplate().setRequestFactory(factory);
        return client;
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("services/platform-service"))) { return current; }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot find the reactor root for real platform HTTP tests");
    }

    private static void awaitPlatform(Path log) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline && PLATFORM_PROCESS.isAlive()) {
            try {
                var response = PLATFORM_HTTP.getForEntity(PLATFORM_URL + "/actuator/health", Map.class);
                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null
                        && "UP".equals(response.getBody().get("status"))) {
                    var login = PLATFORM_HTTP.postForEntity(PLATFORM_URL + "/api/v1/auth/login",
                            Map.of("username", "demo_admin", "password", SEED_PASSWORD), Map.class);
                    // The HTTP server becomes healthy before local ApplicationRunner seed commits.
                    if (login.getStatusCode() == HttpStatus.OK) { return; }
                }
            } catch (RestClientException ignored) {
                // A process that has not bound its port is not yet ready; the overall deadline is fixed.
            }
            try { Thread.sleep(100); }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                stop(PLATFORM_PROCESS);
                throw new IllegalStateException("Interrupted while starting the real platform service", error);
            }
        }
        stop(PLATFORM_PROCESS);
        throw new IllegalStateException("Real platform service did not become healthy; inspect " + log);
    }

    private static void stop(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) { process.destroyForcibly(); }
        } catch (InterruptedException error) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary
        AdjustableClock reservationTestClock() { return new AdjustableClock(); }
    }

    static final class AdjustableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(INITIAL_TIME);

        void set(Instant value) { now.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) {
            return new Clock() {
                @Override public ZoneId getZone() { return zone; }
                @Override public Clock withZone(ZoneId other) { return AdjustableClock.this.withZone(other); }
                @Override public Instant instant() { return now.get(); }
            };
        }
        @Override public Instant instant() { return now.get(); }
    }
}
