package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;

import cn.campus.resource.testing.DatabaseTestSupport;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RuntimeEndpointsTest {
    @Container
    private static final PostgreSQLContainer<?> DATABASE = DatabaseTestSupport.newContainer();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        DatabaseTestSupport.register(properties, DATABASE, "platform");
        properties.add("campus.seed.password", () -> DatabaseTestSupport.SEED_PASSWORD);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private LocalDemoSeed localDemoSeed;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private BuildProperties build;

    @Test
    void migratesItsSchemaAndEnforcesDatabaseOwnership() throws SQLException {
        DatabaseTestSupport.assertMigratedAndIsolated(dataSource, flyway, DATABASE, "platform");
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
                "service", "platform-service",
                "version", build.getVersion(),
                "gitCommit", build.get("gitCommit"),
                "buildTime", build.getTime().toString()));
        assertThat(build.get("gitCommit")).matches("[0-9a-f]{40}([0-9a-f]{24})?(-dirty)?");
        assertThat(Instant.parse((String) response.getBody().get("buildTime")))
                .isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void deniesUnimplementedAndSensitivePaths() {
        for (String path : new String[] {"/actuator/env", "/actuator", "/login", "/actuator/health/liveness"}) {
            assertThat(http.getForEntity(path, String.class).getStatusCode())
                    .as(path).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(http.getForEntity("/api/v1/users", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.getForEntity("/internal/v1/room-blocks", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void doesNotAllowWritesToPublicReadOnlyEndpoints() {
        assertThat(http.postForEntity("/actuator/info", Map.of(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.postForEntity("/actuator/health", Map.of(), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void seedsOnlyTheSpecifiedDemoUsersRolesAndSpaces() {
        assertMinimumSeedCounts();
        assertThat(jdbc.queryForList("SELECT code FROM platform.role", String.class))
                .containsExactlyInAnyOrder("STUDENT", "ROOM_ADMIN", "DEPARTMENT_ADMIN",
                        "PROPERTY_STAFF", "PROPERTY_MANAGER", "OPERATOR", "SYSTEM_ADMIN");
        var userRoles = jdbc.queryForList("""
                SELECT u.username || ':' || r.code
                FROM platform."user" u
                JOIN platform.user_role ur ON ur.user_id = u.id
                JOIN platform.role r ON r.id = ur.role_id
                """, String.class);
        assertThat(userRoles).containsExactlyInAnyOrder(
                "demo_admin:SYSTEM_ADMIN", "demo_student_01:STUDENT", "demo_student_02:STUDENT",
                "demo_room_admin:ROOM_ADMIN", "demo_property_staff:PROPERTY_STAFF");
        var encoder = new BCryptPasswordEncoder();
        for (String hash : jdbc.queryForList("SELECT password_hash FROM platform.\"user\"", String.class)) {
            assertThat(encoder.matches(DatabaseTestSupport.SEED_PASSWORD, hash)).isTrue();
        }
        assertThat(jdbc.queryForList("""
                SELECT COUNT(*) FROM platform.seat GROUP BY room_id
                """, Long.class)).containsExactlyInAnyOrder(10L, 10L, 10L, 10L);
    }

    @Test
    void replayingSeedDoesNotDuplicateDataOrResetAnExistingPassword() throws Exception {
        String originalHash = jdbc.queryForObject(
                "SELECT password_hash FROM platform.\"user\" WHERE username = 'demo_admin'", String.class);
        String changedHash = new BCryptPasswordEncoder(4).encode("test-only-changed-existing-password");
        try {
            jdbc.update("UPDATE platform.\"user\" SET password_hash = ? WHERE username = 'demo_admin'", changedHash);
            var originalHashes = jdbc.queryForList(
                    "SELECT username, password_hash FROM platform.\"user\" ORDER BY username");
            localDemoSeed.run(new DefaultApplicationArguments());
            assertMinimumSeedCounts();
            assertThat(jdbc.queryForList(
                    "SELECT username, password_hash FROM platform.\"user\" ORDER BY username"))
                    .containsExactlyElementsOf(originalHashes);
        } finally {
            jdbc.update("UPDATE platform.\"user\" SET password_hash = ? WHERE username = 'demo_admin'", originalHash);
        }
    }

    private void assertMinimumSeedCounts() {
        Map<String, Integer> expected = Map.of(
                "role", 7, "user", 5, "user_role", 5, "permission", 14,
                "role_permission", 35, "building", 2, "room", 4, "seat", 40);
        expected.forEach((table, count) -> assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM platform.\"" + table + "\"", Integer.class))
                .as("platform.%s", table).isEqualTo(count));
    }
}
