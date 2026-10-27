package cn.campus.resource.maintenance;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
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
        source.registerCorsConfiguration("/api/v1/maintenance-tickets", cors);
        source.registerCorsConfiguration("/api/v1/maintenance-tickets/**", cors);
        return http.csrf(AbstractHttpConfigurer::disable).cors(config -> config.configurationSource(source))
                .addFilterBefore(new RemoteAuthenticationFilter(platform, json), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/maintenance-tickets", "/api/v1/maintenance-tickets/**")
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
