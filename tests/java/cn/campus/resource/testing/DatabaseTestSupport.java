package cn.campus.resource.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

public final class DatabaseTestSupport {
    public static final String SEED_PASSWORD = "test-only-local-demo-password-2026";
    private static final List<String> SCHEMAS =
            List.of("platform", "reservation", "classroom", "maintenance");

    private DatabaseTestSupport() {
    }

    public static PostgreSQLContainer<?> newContainer() {
        var database = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("campus_resource")
                .withUsername("postgres")
                .withPassword("test-only-postgres-bootstrap-password")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("postgres/010-service-databases.sh", 0755),
                        "/docker-entrypoint-initdb.d/010-service-databases.sh");
        for (String schema : SCHEMAS) {
            database.withEnv(schema.toUpperCase(java.util.Locale.ROOT) + "_DB_PASSWORD",
                    appPassword(schema));
            database.withEnv(schema.toUpperCase(java.util.Locale.ROOT) + "_FLYWAY_PASSWORD",
                    migratorPassword(schema));
        }
        return database;
    }

    public static String jdbcUrl(PostgreSQLContainer<?> database, String schema) {
        return database.getJdbcUrl() + (database.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=" + schema;
    }

    public static String appPassword(String schema) {
        return "test-only-" + schema + "-application-password";
    }

    public static String migratorPassword(String schema) {
        return "test-only-" + schema + "-migration-password";
    }

    public static void register(DynamicPropertyRegistry properties,
            PostgreSQLContainer<?> database, String schema) {
        properties.add("spring.datasource.url", () -> jdbcUrl(database, schema));
        properties.add("spring.datasource.username", () -> schema + "_app");
        properties.add("spring.datasource.password", () -> appPassword(schema));
        properties.add("spring.flyway.user", () -> schema + "_migrator");
        properties.add("spring.flyway.password", () -> migratorPassword(schema));
        if (schema.equals("platform")) {
            properties.add("campus.jwt.secret", () -> "test-only-platform-jwt-secret-at-least-32-bytes");
            properties.add("campus.business-zone", () -> "Asia/Shanghai");
            properties.add("campus.internal.reservation-token", () -> "test-only-reservation-internal-token-at-least-32-bytes");
            properties.add("campus.internal.classroom-token", () -> "test-only-classroom-internal-token-at-least-32-bytes");
            properties.add("campus.internal.maintenance-token", () -> "test-only-maintenance-internal-token-at-least-32-bytes");
        } else if (schema.equals("reservation")) {
            properties.add("campus.reservation.scheduler-enabled", () -> "false");
            properties.add("campus.platform.base-url", () -> "http://127.0.0.1:9");
            properties.add("campus.platform.service-token", () -> "test-only-reservation-internal-token-at-least-32-bytes");
            properties.add("campus.business-zone", () -> "Asia/Shanghai");
        } else if (schema.equals("classroom")) {
            properties.add("campus.platform.base-url", () -> "http://127.0.0.1:9");
            properties.add("campus.platform.service-token", () -> "test-only-classroom-internal-token-at-least-32-bytes");
            properties.add("campus.reservation.base-url", () -> "http://127.0.0.1:9");
            properties.add("campus.reservation.service-token", () -> "test-only-m1-classroom-token-at-least-32-bytes");
            properties.add("campus.classroom.scheduler-enabled", () -> "false");
        }
    }

    public static void assertMigratedAndIsolated(DataSource dataSource, Flyway flyway,
            PostgreSQLContainer<?> database, String ownSchema) throws SQLException {
        flyway.validate();
        assertThat(flyway.info().current()).isNotNull();
        int version = ownSchema.equals("platform") ? 4 : ownSchema.equals("reservation") ? 3 : ownSchema.equals("classroom") ? 2 : 1;
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo(Integer.toString(version));
        assertThat(flyway.info().applied()).hasSize(version);

        try (Connection app = dataSource.getConnection();
                Connection migrator = migratorConnection(database, ownSchema)) {
            try (var statement = app.createStatement();
                    var result = statement.executeQuery("SELECT current_user, current_schema()")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo(ownSchema + "_app");
                assertThat(result.getString(2)).isEqualTo(ownSchema);
            }
            assertDenied(app, "CREATE TABLE " + ownSchema + ".app_ddl_probe (id BIGINT)");
            assertDenied(app, "SET ROLE " + ownSchema + "_migrator");
            assertDenied(app, "SELECT * FROM " + ownSchema + ".flyway_schema_history");
            assertDenied(app, "UPDATE " + ownSchema
                    + ".flyway_schema_history SET description = description WHERE FALSE");
            assertDenied(app, "DELETE FROM " + ownSchema + ".flyway_schema_history WHERE FALSE");

            // Use real tables so a missing relation cannot masquerade as access isolation.
            try {
                for (String schema : SCHEMAS) {
                    try (Connection owner = migratorConnection(database, schema)) {
                        execute(owner, "CREATE TABLE " + schema
                                + ".access_probe (id BIGINT PRIMARY KEY, value TEXT NOT NULL)");
                    }
                }

                execute(app, "INSERT INTO " + ownSchema + ".access_probe VALUES (1, 'before')");
                execute(app, "UPDATE " + ownSchema + ".access_probe SET value = 'after' WHERE id = 1");
                try (var statement = app.createStatement();
                        var result = statement.executeQuery(
                                "SELECT value FROM " + ownSchema + ".access_probe WHERE id = 1")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("after");
                    assertThat(result.next()).isFalse();
                }
                execute(app, "DELETE FROM " + ownSchema + ".access_probe WHERE id = 1");

                for (String schema : SCHEMAS) {
                    if (!schema.equals(ownSchema)) {
                        for (Connection connection : List.of(app, migrator)) {
                            assertDenied(connection, "SELECT * FROM " + schema + ".access_probe");
                            assertDenied(connection, "INSERT INTO " + schema
                                    + ".access_probe VALUES (1, 'forbidden')");
                            assertDenied(connection, "UPDATE " + schema
                                    + ".access_probe SET value = 'forbidden' WHERE FALSE");
                            assertDenied(connection, "DELETE FROM " + schema + ".access_probe WHERE FALSE");
                        }
                    }
                }
            } finally {
                for (String schema : SCHEMAS) {
                    try (Connection owner = migratorConnection(database, schema)) {
                        execute(owner, "DROP TABLE IF EXISTS " + schema + ".access_probe");
                    }
                }
            }
        }
    }

    private static Connection migratorConnection(PostgreSQLContainer<?> database, String schema)
            throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database, schema),
                schema + "_migrator", migratorPassword(schema));
    }

    private static void assertDenied(Connection connection, String sql) {
        SQLException denied = assertThrows(SQLException.class, () -> execute(connection, sql), sql);
        assertThat(denied.getSQLState()).as(sql).isEqualTo("42501");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
