package cn.campus.resource.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration(proxyBeanMethods = false)
class PlatformSecurity {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean ZoneId businessZone(@Value("${campus.business-zone}") String value) {
        return ZoneId.of(value);
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean SecretKey jwtKey(@Value("${campus.jwt.secret}") String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (value.isBlank() || bytes.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 UTF-8 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtKey));
    }

    @Bean JwtDecoder jwtDecoder(SecretKey jwtKey, Clock clock,
            @Value("${campus.jwt.issuer}") String issuer, @Value("${campus.jwt.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator time = new JwtTimestampValidator(Duration.ZERO);
        time.setClock(clock);
        OAuth2TokenValidator<Jwt> required = jwt -> {
            boolean valid = jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && jwt.getNotBefore() != null && jwt.getSubject() != null
                    && jwt.getAudience() != null && jwt.getAudience().contains(audience)
                    && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    && jwt.getExpiresAt().isAfter(clock.instant())
                    && !jwt.getIssuedAt().isAfter(clock.instant());
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time, new JwtIssuerValidator(issuer), required));
        return decoder;
    }

    @Bean @Order(1)
    SecurityFilterChain internalSecurity(HttpSecurity http, ObjectMapper json,
            @Value("${campus.internal.reservation-token}") String reservation,
            @Value("${campus.internal.classroom-token}") String classroom,
            @Value("${campus.internal.maintenance-token}") String maintenance) throws Exception {
        return http.securityMatcher("/internal/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(new InternalTokenFilter(json, reservation, classroom, maintenance),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/internal/v1/rooms", "/internal/v1/rooms/**", "/internal/v1/settings")
                            .hasAuthority("SERVICE_READ")
                        .requestMatchers(HttpMethod.GET, "/internal/v1/maintenance-staff/*").hasAuthority("SERVICE_MAINTENANCE")
                        .requestMatchers(HttpMethod.POST, "/internal/v1/checkin-codes/verify", "/internal/v1/notifications")
                            .hasAuthority("SERVICE_RESERVATION")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                ApiErrors.write(json, response, 401, "UNAUTHORIZED", "内部服务身份无效"))
                        .accessDeniedHandler((request, response, error) ->
                                ApiErrors.write(json, response, 403, "FORBIDDEN", "无权调用此内部接口")))
                .build();
    }

    @Bean @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper json, JwtDecoder decoder,
            IdentityMapper identities, @Value("${campus.cors.origins}") List<String> origins) throws Exception {
        if (origins.isEmpty() || origins.stream().anyMatch(value -> value.contains("*"))) {
            throw new IllegalStateException("CORS requires explicit origins");
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("X-Request-ID"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", cors);
        return http.csrf(AbstractHttpConfigurer::disable).cors(config -> config.configurationSource(source))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(token -> authenticate(token, identities)))
                        .authenticationEntryPoint((request, response, error) -> {
                            boolean unavailable = error instanceof OAuth2AuthenticationException oauthError
                                    && oauthError.getError().getErrorCode().equals("temporarily_unavailable");
                            ApiErrors.write(json, response, unavailable ? 503 : 401,
                                    unavailable ? "DATABASE_UNAVAILABLE" : "UNAUTHORIZED",
                                    unavailable ? "身份存储暂不可用" : "登录凭证无效或已过期");
                        }))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> ApiErrors.write(json, response,
                                request.getRequestURI().startsWith("/api/v1/") ? 401 : 403,
                                request.getRequestURI().startsWith("/api/v1/") ? "UNAUTHORIZED" : "FORBIDDEN", "请使用授权接口"))
                        .accessDeniedHandler((request, response, error) ->
                                ApiErrors.write(json, response, 403, "FORBIDDEN", "无权执行此操作")))
                .build();
    }

    private UsernamePasswordAuthenticationToken authenticate(Jwt jwt, IdentityMapper identities) {
        long id;
        Object version = jwt.getClaim("authVersion");
        try { id = Long.parseLong(jwt.getSubject()); }
        catch (NumberFormatException error) { throw invalid(); }
        if (id <= 0 || !(version instanceof Long || version instanceof Integer)) { throw invalid(); }
        long authVersion = ((Number) version).longValue();
        if (authVersion < 0) { throw invalid(); }
        try {
            UserAccount user = identities.byId(id);
            if (user == null || !user.enabled() || user.authVersion() != authVersion) { throw invalid(); }
            Actor actor = new Actor(user.id(), user.username(), user.displayName(),
                    identities.roles(user.id()), identities.permissions(user.id()));
            MDC.put("userId", Long.toString(user.id()));
            return UsernamePasswordAuthenticationToken.authenticated(actor, null,
                    actor.permissions().stream().map(SimpleGrantedAuthority::new).toList());
        } catch (DataAccessException failure) {
            throw new OAuth2AuthenticationException(new OAuth2Error("temporarily_unavailable"));
        }
    }

    private OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
    }
}
