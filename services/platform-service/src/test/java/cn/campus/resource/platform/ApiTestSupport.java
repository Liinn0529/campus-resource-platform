package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;

import cn.campus.resource.testing.DatabaseTestSupport;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiTestSupport {
    protected static final String SEED_PASSWORD = DatabaseTestSupport.SEED_PASSWORD;
    protected static final String JWT_SECRET = "test-only-jwt-signing-secret-at-least-thirty-two-bytes-2026";
    protected static final String RESERVATION_TOKEN = "test-only-reservation-service-token-at-least-thirty-two-bytes";
    protected static final String CLASSROOM_TOKEN = "test-only-classroom-service-token-at-least-thirty-two-bytes";
    protected static final String MAINTENANCE_TOKEN = "test-only-maintenance-service-token-at-least-thirty-two-bytes";

    private static final PostgreSQLContainer<?> DATABASE = DatabaseTestSupport.newContainer();

    static {
        // One container outlives every cached Spring context; per-class @Container would stop it too soon.
        DATABASE.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        DatabaseTestSupport.register(properties, DATABASE, "platform");
        properties.add("campus.seed.password", () -> SEED_PASSWORD);
        properties.add("campus.jwt.secret", () -> JWT_SECRET);
        properties.add("campus.jwt.issuer", () -> "https://campus-resource.local/platform");
        properties.add("campus.jwt.audience", () -> "campus-web");
        properties.add("campus.jwt.ttl", () -> "PT15M");
        properties.add("campus.business-zone", () -> "Asia/Shanghai");
        properties.add("campus.internal.reservation-token", () -> RESERVATION_TOKEN);
        properties.add("campus.internal.classroom-token", () -> CLASSROOM_TOKEN);
        properties.add("campus.internal.maintenance-token", () -> MAINTENANCE_TOKEN);
    }

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected JdbcTemplate jdbc;

    protected String login(String username) {
        var response = request(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", username, "password", SEED_PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object token = data(response).get("accessToken");
        assertThat(token instanceof String).isTrue();
        return (String) token;
    }

    protected ResponseEntity<Map> request(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    protected ResponseEntity<Map> internalRequest(HttpMethod method, String path, Object body, String token) {
        return request(method, path, body, token);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> data(ResponseEntity<Map> response) {
        assertThat(response.getBody() != null).isTrue();
        Object value = response.getBody().get("data");
        assertThat(value instanceof Map).isTrue();
        return (Map<String, Object>) value;
    }

    protected long userid(String username) {
        return jdbc.queryForObject("SELECT id FROM platform.\"user\" WHERE username = ?", Long.class, username);
    }

    protected long userId(String username) {
        return userid(username);
    }
}
