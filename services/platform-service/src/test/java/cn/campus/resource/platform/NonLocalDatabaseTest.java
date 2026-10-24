package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;

import cn.campus.resource.testing.DatabaseTestSupport;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NonLocalDatabaseTest {
    @Container
    private static final PostgreSQLContainer<?> DATABASE = DatabaseTestSupport.newContainer();

    @Test
    void startsAndMigratesWithoutLocalDemoSeedOrItsPassword() {
        try (var application = new SpringApplicationBuilder(Application.class)
                .web(WebApplicationType.SERVLET)
                .run("--spring.profiles.active=database-test", "--server.port=0",
                        "--spring.datasource.url=" + DatabaseTestSupport.jdbcUrl(DATABASE, "platform"),
                        "--spring.datasource.username=platform_app",
                        "--spring.datasource.password=" + DatabaseTestSupport.appPassword("platform"),
                        "--spring.flyway.user=platform_migrator",
                        "--spring.flyway.password=" + DatabaseTestSupport.migratorPassword("platform"),
                        "--campus.jwt.secret=test-only-nonlocal-jwt-secret-at-least-32-bytes",
                        "--campus.business-zone=Asia/Shanghai",
                        "--campus.internal.reservation-token=test-only-reservation-internal-token-at-least-32-bytes",
                        "--campus.internal.classroom-token=test-only-classroom-internal-token-at-least-32-bytes",
                        "--campus.internal.maintenance-token=test-only-maintenance-internal-token-at-least-32-bytes")) {
            assertThat(application.getBeansOfType(LocalDemoSeed.class)).isEmpty();
            var flyway = application.getBean(Flyway.class);
            flyway.validate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
            var jdbc = application.getBean(JdbcTemplate.class);
            for (String table : List.of("user", "user_role", "building", "room", "seat",
                    "user_room_scope", "room_checkin_code", "audit_log")) {
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM platform.\"" + table + "\"", Integer.class))
                        .as("non-local platform.%s must be empty", table).isZero();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.role", Integer.class)).isEqualTo(7);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.permission", Integer.class)).isEqualTo(14);
        }
    }
}
