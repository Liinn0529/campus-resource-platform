package cn.campus.resource.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import cn.campus.resource.testing.DatabaseTestSupport;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RuntimeEndpointsTest {
    @Container
    private static final PostgreSQLContainer<?> DATABASE = DatabaseTestSupport.newContainer();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        DatabaseTestSupport.register(properties, DATABASE, "reservation");
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private BuildProperties build;

    @Test
    void migratesItsSchemaAndEnforcesDatabaseOwnership() throws SQLException {
        DatabaseTestSupport.assertMigratedAndIsolated(dataSource, flyway, DATABASE, "reservation");
    }

    @Test
    void exposesHealthWithoutLeakingDetails() {
        var response = http.getForEntity("/actuator/health", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactlyEntriesOf(Map.of("status", "UP"));
    }

    @Test
    void exposesFlatMetadataFromTheActualBuild() {
        var response = http.getForEntity("/actuator/info", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "service", "reservation-service",
                "version", build.getVersion(),
                "gitCommit", build.get("gitCommit"),
                "buildTime", build.getTime().toString()));
        assertThat(build.get("gitCommit")).matches("[0-9a-f]{40}([0-9a-f]{24})?(-dirty)?");
        assertThat(Instant.parse((String) response.getBody().get("buildTime")))
                .isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void deniesUnimplementedAndSensitivePaths() {
        for (String path : new String[] {"/actuator/env", "/actuator", "/api/v1/users",
                "/internal/v1/room-blocks", "/login", "/actuator/health/liveness"}) {
            assertThat(http.getForEntity(path, String.class).getStatusCode())
                    .as(path).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void doesNotAllowWritesToPublicReadOnlyEndpoints() {
        assertThat(http.postForEntity("/actuator/info", Map.of(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.postForEntity("/actuator/health", Map.of(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
