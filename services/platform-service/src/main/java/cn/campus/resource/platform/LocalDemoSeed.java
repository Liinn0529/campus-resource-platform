package cn.campus.resource.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
class LocalDemoSeed implements ApplicationRunner {
    private final NamedParameterJdbcTemplate jdbc;
    private final String password;

    LocalDemoSeed(NamedParameterJdbcTemplate jdbc, @Value("${campus.seed.password}") String password) {
        this.jdbc = jdbc;
        this.password = password;
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public void run(ApplicationArguments arguments) throws IOException {
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.isBlank() || passwordBytes < 24 || passwordBytes > 72) {
            throw new IllegalStateException("Local demo seed password must contain 24 to 72 UTF-8 bytes.");
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        Map<String, String> hashes = Map.of(
                "adminHash", encoder.encode(password),
                "student1Hash", encoder.encode(password),
                "student2Hash", encoder.encode(password),
                "roomAdminHash", encoder.encode(password),
                "propertyStaffHash", encoder.encode(password));

        execute("roles.sql", Map.of());
        execute("users.sql", hashes);
        execute("user_roles.sql", Map.of());
        execute("buildings.sql", Map.of());
        execute("rooms.sql", Map.of());
        execute("seats.sql", Map.of());
    }

    private void execute(String filename, Map<String, ?> parameters) throws IOException {
        ClassPathResource resource = new ClassPathResource("db/seed/local/" + filename);
        jdbc.update(resource.getContentAsString(StandardCharsets.UTF_8), parameters);
    }
}
