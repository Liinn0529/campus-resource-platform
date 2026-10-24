package cn.campus.resource.platform;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthenticationService {
    private final IdentityMapper identities;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final Clock clock;
    private final Duration ttl;
    private final String issuer;
    private final String audience;
    private final AccessPolicy access;
    private final AuditService audit;
    private final String dummyHash;

    AuthenticationService(IdentityMapper identities, PasswordEncoder passwords, JwtEncoder tokens,
            Clock clock, @Value("${campus.jwt.ttl}") Duration ttl,
            @Value("${campus.jwt.issuer}") String issuer, @Value("${campus.jwt.audience}") String audience,
            AccessPolicy access, AuditService audit) {
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalStateException("JWT lifetime must be positive and at most one hour");
        }
        this.identities = identities;
        this.passwords = passwords;
        this.tokens = tokens;
        this.clock = clock;
        this.ttl = ttl;
        this.issuer = issuer;
        this.audience = audience;
        this.access = access;
        this.audit = audit;
        this.dummyHash = passwords.encode("unused-timing-only-password");
    }

    LoginResult login(String username, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        UserAccount user = identities.byUsername(username);
        boolean matched = passwords.matches(password, user == null ? dummyHash : user.passwordHash());
        if (!matched || user == null || !user.enabled()) { throw invalidCredentials(); }
        Instant issued = clock.instant();
        Instant expires = issued.plus(ttl);
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(Long.toString(user.id()))
                .audience(List.of(audience)).issuedAt(issued).notBefore(issued).expiresAt(expires)
                .claim("authVersion", user.authVersion()).build();
        String token = tokens.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new LoginResult(token, "Bearer", expires, actor(user));
    }

    Actor actor(UserAccount user) {
        return new Actor(user.id(), user.username(), user.displayName(),
                identities.roles(user.id()), identities.permissions(user.id()));
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        PasswordRules.validate(newPassword);
        UserAccount user = identities.byId(access.actor().id());
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(currentPassword, user.passwordHash())) {
            throw invalidCredentials();
        }
        if (identities.changePassword(user.id(), user.authVersion(), passwords.encode(newPassword)) != 1) {
            throw new ApiFailure(HttpStatus.CONFLICT, "IDENTITY_CHANGED", "用户身份已变更，请重新登录");
        }
        audit.record("PASSWORD_CHANGED", "USER", user.id());
    }

    private ApiFailure invalidCredentials() {
        return new ApiFailure(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "用户名或密码不正确");
    }

    record LoginResult(String accessToken, String tokenType, Instant expiresAt, Actor user) {
        @Override
        public String toString() { return "LoginResult[credentials=redacted]"; }
    }
}
