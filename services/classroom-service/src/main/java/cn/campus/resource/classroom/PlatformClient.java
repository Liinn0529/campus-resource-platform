package cn.campus.resource.classroom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class PlatformClient {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Identity(long id, String username, Set<String> roles, Set<String> permissions) { }
    record Room(long id, long buildingId, String code, String name, String type,
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) boolean enabled) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Envelope<T>(T data, String requestId) { }

    private final RestClient http;
    private final String serviceToken;

    PlatformClient(RestClient.Builder builder, @Value("${campus.platform.base-url}") String base,
            @Value("${campus.platform.service-token}") String serviceToken) {
        URI uri = URI.create(base);
        if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                || serviceToken.isBlank() || serviceToken.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Configure an HTTP platform origin and a private service credential");
        }
        this.serviceToken = serviceToken;
        var transport = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(transport);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.baseUrl(base).requestFactory(factory).build();
    }

    Actor identity(String bearer) {
        Identity user = get("/api/v1/me", bearer, new ParameterizedTypeReference<Envelope<Identity>>() { }, true);
        if (user.id() <= 0 || user.username() == null || user.username().isBlank()
                || user.username().length() > 64 || user.roles() == null || user.roles().size() > 7
                || user.permissions() == null || user.permissions().size() > 100
                || user.roles().stream().anyMatch(value -> value == null || !value.matches("[A-Z_]{1,64}"))
                || user.permissions().stream().anyMatch(value -> value == null
                        || !value.matches("[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*){1,3}"))) {
            throw unavailable();
        }
        Set<Long> rooms = Set.of();
        if (user.permissions().contains("classroom:approve")) {
            List<Long> rows = get("/api/v1/me/room-scopes", bearer,
                    new ParameterizedTypeReference<Envelope<List<Long>>>() { }, true);
            if (rows.size() > 1000) { throw unavailable(); }
            long previous = 0;
            for (Long id : rows) {
                if (id == null || id <= previous) { throw unavailable(); }
                previous = id;
            }
            rooms = Set.copyOf(rows);
        }
        return new Actor(user.id(), user.username(), user.roles(), user.permissions(), rooms);
    }

    Room room(long id) {
        Room room = get("/internal/v1/rooms/" + id, serviceToken,
                new ParameterizedTypeReference<Envelope<Room>>() { }, false);
        if (room.id() != id || room.buildingId() <= 0 || room.code() == null || room.code().isBlank()
                || room.code().length() > 64 || room.name() == null || room.name().isBlank()
                || room.name().length() > 100 || room.type() == null
                || !Set.of("STUDY_ROOM", "CLASSROOM").contains(room.type())) {
            throw unavailable();
        }
        return room;
    }

    private <T> T get(String path, String credential, ParameterizedTypeReference<Envelope<T>> type,
            boolean userIdentity) {
        try {
            var response = http.get().uri(path).headers(headers -> headers.setBearerAuth(credential))
                    .retrieve().toEntity(type);
            var body = response.getBody();
            if (response.getStatusCode().value() != 200 || body == null || body.data() == null
                    || body.requestId() == null || body.requestId().isBlank() || body.requestId().length() > 64) {
                throw unavailable();
            }
            return body.data();
        } catch (RestClientResponseException failure) {
            if (userIdentity && failure.getStatusCode().value() == 401) {
                throw new ApiFailure(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录凭证无效或已过期");
            }
            if (userIdentity && failure.getStatusCode().value() == 403) {
                throw new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前无审批权限");
            }
            if (!userIdentity && failure.getStatusCode().value() == 404) {
                throw new ApiFailure(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "请求的资源不存在");
            }
            throw unavailable();
        } catch (RestClientException failure) {
            throw unavailable();
        }
    }

    private ApiFailure unavailable() {
        return new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE", "公共层暂不可用");
    }
}
