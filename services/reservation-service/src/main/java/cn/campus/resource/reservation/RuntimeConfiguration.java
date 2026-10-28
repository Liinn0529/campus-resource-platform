package cn.campus.resource.reservation;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
class RuntimeConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean ZoneId businessZone(@Value("${campus.business-zone}") String zone) { return ZoneId.of(zone); }
    @Bean
    InfoContributor serviceInfo(BuildProperties build) {
        return builder -> builder
                .withDetail("service", build.getArtifact())
                .withDetail("version", build.getVersion())
                .withDetail("gitCommit", build.get("gitCommit"))
                .withDetail("buildTime", build.getTime().toString());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, PlatformClient platform, ObjectMapper json,
            @Value("${campus.internal.classroom-token}") String classroomToken,
            @Value("${campus.internal.maintenance-token}") String maintenanceToken,
            @Value("${campus.platform.service-token}") String platformToken,
            @Value("${campus.cors.origins}") List<String> origins) throws Exception {
        if (origins.isEmpty() || origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("CORS requires explicit origins");
        }
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("X-Request-ID"));
        cors.setAllowCredentials(false);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/reservation/**", cors);
        source.registerCorsConfiguration("/api/v1/reservations", cors);
        source.registerCorsConfiguration("/api/v1/violations", cors);
        source.registerCorsConfiguration("/api/v1/reservations/**", cors);
        return http.csrf(AbstractHttpConfigurer::disable).cors(config -> config.configurationSource(source))
                .addFilterBefore(new RemoteAuthenticationFilter(platform, json), AnonymousAuthenticationFilter.class)
                .addFilterBefore(new InternalAuthenticationFilter(classroomToken, maintenanceToken, platformToken, json),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/internal/v1/room-blocks", "/internal/v1/room-blocks/**")
                            .hasAuthority("OCCUPANCY_SERVICE")
                        .requestMatchers("/api/v1/reservation/**", "/api/v1/reservations", "/api/v1/reservations/**", "/api/v1/violations")
                            .authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> ApiErrors.write(json, response,
                                RemoteAuthenticationFilter.publicApi(request.getRequestURI()) ? 401 : 403,
                                RemoteAuthenticationFilter.publicApi(request.getRequestURI()) ? "UNAUTHORIZED" : "FORBIDDEN",
                                "请使用授权接口"))
                        .accessDeniedHandler((request, response, error) ->
                                ApiErrors.write(json, response, 403, "FORBIDDEN", "无权执行此操作")))
                .build();
    }
}
