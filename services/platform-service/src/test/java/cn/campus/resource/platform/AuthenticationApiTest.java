package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationApiTest extends ApiTestSupport {
    private static final String STUDENT = "demo_student_01";
    private final List<UserState> originalUsers = new ArrayList<>();

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private SecretKey key;

    @BeforeEach
    void rememberSeedAuthenticationState() {
        originalUsers.clear();
        jdbc.query("SELECT id, password_hash, enabled, auth_version FROM platform.\"user\"", row -> {
            long id = row.getLong("id");
            originalUsers.add(new UserState(id, row.getString("password_hash"), row.getBoolean("enabled"),
                    row.getLong("auth_version"), jdbc.queryForList(
                            "SELECT role_id FROM platform.user_role WHERE user_id = ?", Long.class, id)));
        });
    }

    @AfterEach
    void restoreAuthenticationState() {
        for (UserState user : originalUsers) {
            jdbc.update("UPDATE platform.\"user\" SET password_hash = ?, enabled = ?, auth_version = ? WHERE id = ?",
                    user.passwordHash(), user.enabled(), user.authVersion(), user.id());
            jdbc.update("DELETE FROM platform.user_role WHERE user_id = ?", user.id());
            for (long role : user.roles()) {
                jdbc.update("INSERT INTO platform.user_role (user_id, role_id) VALUES (?, ?)", user.id(), role);
            }
        }
    }

    @Test
    void authenticatesAgainstTheRealSeedHashAndReturnsCurrentIdentity() {
        Instant startedAt = Instant.now();
        var response = request(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", STUDENT, "password", SEED_PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertRequestId(response);
        var result = data(response);
        assertThat(result.get("tokenType")).isEqualTo("Bearer");
        assertThat(result.get("accessToken") instanceof String).isTrue();
        String token = (String) result.get("accessToken");
        var jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(userid(STUDENT)));
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://campus-resource.local/platform");
        assertThat(jwt.getAudience()).containsExactly("campus-web");
        assertThat(jwt.getExpiresAt()).isAfter(startedAt.plusSeconds(14 * 60))
                .isBefore(startedAt.plusSeconds(16 * 60));
        assertThat(Duration.between(jwt.getExpiresAt(), Instant.parse((String) result.get("expiresAt"))).abs())
                .isLessThan(Duration.ofSeconds(1));

        var current = request(HttpMethod.GET, "/api/v1/me", null, token);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertRequestId(current);
        var user = data(current);
        assertThat(((Number) user.get("id")).longValue()).isEqualTo(userid(STUDENT));
        assertThat(user.get("username")).isEqualTo(STUDENT);
        assertThat(user.get("displayName")).isInstanceOf(String.class);
        assertThat(user.get("roles")).isEqualTo(List.of("STUDENT"));
        assertThat(user.get("permissions")).isInstanceOf(List.class);
        assertThat(result.get("user")).isEqualTo(user);
        assertThat(user.containsKey("passwordHash") || user.containsKey("password_hash")).isFalse();
    }

    @Test
    void wrongUnknownAndDisabledCredentialsHaveTheSameGenericFailure() {
        var wrong = request(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", STUDENT, "password", "test-only-wrong-password"), null);
        var unknown = request(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", "test-only-user-who-does-not-exist", "password", SEED_PASSWORD), null);
        jdbc.update("UPDATE platform.\"user\" SET enabled = FALSE WHERE username = ?", STUDENT);
        var disabled = request(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", STUDENT, "password", SEED_PASSWORD), null);
        for (var response : List.of(wrong, unknown, disabled)) {
            assertError(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED");
            assertThat(response.getBody().get("message")).isEqualTo(wrong.getBody().get("message"));
            assertNoSensitiveValues(response, SEED_PASSWORD, "test-only-wrong-password", STUDENT);
        }
    }

    @Test
    void missingAndMalformedLoginInputsAreValidationErrorsWithoutEchoingPasswords() {
        for (Map<String, String> body : List.of(
                Map.of("username", STUDENT),
                Map.of("username", "", "password", "test-only-password-not-for-response"))) {
            var response = request(HttpMethod.POST, "/api/v1/auth/login", body, null);
            assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
            assertNoSensitiveValues(response, "test-only-password-not-for-response");
        }
    }

    @Test
    void unauthenticatedAndServiceCredentialsCannotAccessAUserIdentity() {
        for (String token : new String[] {null, "test-only-malformed-jwt", RESERVATION_TOKEN}) {
            var response = request(HttpMethod.GET, "/api/v1/me", null, token);
            assertError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
            if (token != null) {
                assertNoSensitiveValues(response, token);
            }
        }
    }

    @Test
    void aStudentCannotManageUsersRolesPermissionsOrReadAudit() {
        String token = login(STUDENT);
        for (String path : List.of("/api/v1/users", "/api/v1/roles", "/api/v1/permissions", "/api/v1/audit-events")) {
            var response = request(HttpMethod.GET, path, null, token);
            assertError(response, HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
    }

    @Test
    void disablingAUserRejectsAnAlreadyIssuedToken() {
        String token = login(STUDENT);
        jdbc.update("UPDATE platform.\"user\" SET enabled = FALSE WHERE username = ?", STUDENT);
        assertError(request(HttpMethod.GET, "/api/v1/me", null, token),
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @Test
    void authoritiesComeFromCurrentDatabaseAssignmentsRatherThanOldTokenClaims() {
        String token = login(STUDENT);
        assertError(request(HttpMethod.GET, "/api/v1/users", null, token),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        long user = userid(STUDENT);
        long adminRole = jdbc.queryForObject(
                "SELECT id FROM platform.role WHERE code = 'SYSTEM_ADMIN'", Long.class);
        jdbc.update("INSERT INTO platform.user_role (user_id, role_id) VALUES (?, ?)", user, adminRole);
        assertThat(request(HttpMethod.GET, "/api/v1/users", null, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM platform.user_role WHERE user_id = ? AND role_id = ?", user, adminRole);
        assertError(request(HttpMethod.GET, "/api/v1/users", null, token),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    @Test
    void passwordChangeUsesBcryptAndInvalidatesThePreviousTokenAndPassword() {
        String token = login(STUDENT);
        String replacement = "test-only-valid-replacement-password-2026";
        long previousVersion = jdbc.queryForObject(
                "SELECT auth_version FROM platform.\"user\" WHERE username = ?", Long.class, STUDENT);
        var changed = request(HttpMethod.PUT, "/api/v1/me/password",
                Map.of("currentPassword", SEED_PASSWORD, "newPassword", replacement), token);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertRequestId(changed);
        assertNoSensitiveValues(changed, SEED_PASSWORD, replacement);
        String hash = jdbc.queryForObject("SELECT password_hash FROM platform.\"user\" WHERE username = ?",
                String.class, STUDENT);
        assertThat(new BCryptPasswordEncoder().matches(replacement, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT auth_version FROM platform.\"user\" WHERE username = ?",
                Long.class, STUDENT)).isGreaterThan(previousVersion);
        assertError(request(HttpMethod.GET, "/api/v1/me", null, token), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        assertError(request(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", STUDENT, "password", SEED_PASSWORD), null),
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED");
        var refreshed = request(HttpMethod.POST, "/api/v1/auth/login",
                Map.of("username", STUDENT, "password", replacement), null);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newToken = (String) data(refreshed).get("accessToken");
        assertThat(request(HttpMethod.GET, "/api/v1/me", null, newToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rejectsWrongCurrentPasswordAndEnforcesUtf8NewPasswordBoundaries() {
        String token = login(STUDENT);
        var wrong = request(HttpMethod.PUT, "/api/v1/me/password",
                Map.of("currentPassword", "test-only-wrong-current-password", "newPassword", "valid-new-password-2026"),
                token);
        assertError(wrong, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED");
        assertNoSensitiveValues(wrong, "test-only-wrong-current-password", "valid-new-password-2026");
        for (String invalid : List.of("short", "a".repeat(73), "\uD83D\uDD12".repeat(19))) {
            var response = request(HttpMethod.PUT, "/api/v1/me/password",
                    Map.of("currentPassword", SEED_PASSWORD, "newPassword", invalid), token);
            assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
            assertNoSensitiveValues(response, SEED_PASSWORD, invalid);
        }
        assertThat(request(HttpMethod.GET, "/api/v1/me", null, token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(STUDENT) != null).isTrue();
    }

    @Test
    void rejectsTamperingAndUnsignedTokensWithoutReturningTheCredential() {
        String token = login(STUDENT);
        String[] parts = token.split("\\.");
        String modifiedSignature = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        String unsignedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        for (String invalid : List.of(parts[0] + "." + parts[1] + "." + modifiedSignature,
                unsignedHeader + "." + parts[1] + ".")) {
            var response = request(HttpMethod.GET, "/api/v1/me", null, invalid);
            assertError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
            assertNoSensitiveValues(response, invalid);
        }
    }

    @Test
    void rejectsSignedTokensWithInvalidIssuerAudienceExpiryNotBeforeAndAuthenticationVersion() {
        String valid = login(STUDENT);
        Instant now = Instant.now();
        List<Consumer<Map<String, Object>>> invalidClaims = List.of(
                claims -> claims.put("iss", "test-only-untrusted-issuer"),
                claims -> claims.put("aud", List.of("test-only-wrong-audience")),
                claims -> claims.remove("aud"),
                claims -> {
                    claims.put("iat", now.minusSeconds(120));
                    claims.put("exp", now.minusSeconds(60));
                },
                claims -> claims.remove("exp"),
                claims -> claims.put("nbf", now.plusSeconds(60)),
                claims -> claims.put("authVersion", 999999L),
                claims -> claims.put("authVersion", 0.5),
                claims -> claims.remove("authVersion"),
                claims -> claims.put("sub", "999999999"));
        for (Consumer<Map<String, Object>> mutation : invalidClaims) {
            String invalid = signedWithChangedClaims(valid, mutation);
            var response = request(HttpMethod.GET, "/api/v1/me", null, invalid);
            assertError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
            assertNoSensitiveValues(response, invalid);
        }
    }

    @Test
    void rejectsATokenAtItsExactExpiryWithoutClockSkew() {
        Instant expiry = Instant.parse("2026-10-24T12:00:00Z");
        var claims = JwtClaimsSet.builder().issuer("https://campus-resource.local/platform")
                .subject(Long.toString(userid(STUDENT))).audience(List.of("campus-web"))
                .issuedAt(expiry.minusSeconds(60)).notBefore(expiry.minusSeconds(60))
                .expiresAt(expiry).claim("authVersion", 0L).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        var boundary = new PlatformSecurity().jwtDecoder(key, Clock.fixed(expiry, ZoneOffset.UTC),
                "https://campus-resource.local/platform", "campus-web");
        assertThrows(JwtException.class, () -> boundary.decode(token));
        var justBefore = new PlatformSecurity().jwtDecoder(key,
                Clock.fixed(expiry.minusNanos(1), ZoneOffset.UTC),
                "https://campus-resource.local/platform", "campus-web");
        assertThat(justBefore.decode(token).getSubject()).isEqualTo(Long.toString(userid(STUDENT)));
    }

    private String signedWithChangedClaims(String valid, Consumer<Map<String, Object>> mutation) {
        Map<String, Object> claims = new HashMap<>(decoder.decode(valid).getClaims());
        mutation.accept(claims);
        JwtClaimsSet changed = JwtClaimsSet.builder().claims(values -> values.putAll(claims)).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), changed)).getTokenValue();
    }

    private void assertError(ResponseEntity<Map> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody() != null).isTrue();
        assertThat(response.getBody().keySet()).containsExactlyInAnyOrder("code", "message", "requestId");
        assertThat(response.getBody().get("code")).isEqualTo(code);
        assertThat(response.getBody().get("message") instanceof String).isTrue();
        assertRequestId(response);
    }

    private void assertRequestId(ResponseEntity<Map> response) {
        String header = response.getHeaders().getFirst("X-Request-ID");
        assertThat(header).isNotBlank();
        assertThat(response.getBody() != null).isTrue();
        assertThat(response.getBody().get("requestId")).isEqualTo(header);
    }

    private void assertNoSensitiveValues(ResponseEntity<Map> response, String... values) {
        String body = String.valueOf(response.getBody());
        for (String value : values) {
            assertThat(body.contains(value)).as("response must omit sensitive input").isFalse();
        }
    }

    private record UserState(long id, String passwordHash, boolean enabled, long authVersion, List<Long> roles) {
    }
}
