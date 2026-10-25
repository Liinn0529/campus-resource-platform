package cn.campus.resource.reservation;

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
    record Room(long id, long buildingId, String code, String name, String type,
            @JsonProperty(required = true) boolean enabled) { }
    record Seat(long id, long roomId, String code,
            @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) boolean hasPowerOutlet,
            @JsonProperty(required = true) boolean enabled) { }
    record Settings(int maxReservationHours) { }
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
        Actor actor = get("/api/v1/me", bearer, new ParameterizedTypeReference<Envelope<Actor>>() { }, true);
        if (actor.id() <= 0 || actor.username() == null || actor.username().isBlank()
                || actor.username().length() > 64 || actor.roles() == null || actor.roles().size() > 7
                || actor.permissions() == null || actor.permissions().size() > 100
                || actor.roles().stream().anyMatch(value -> value == null || !value.matches("[A-Z_]{1,64}"))
                || actor.permissions().stream().anyMatch(value -> value == null
                        || !value.matches("[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*){1,3}"))) {
            throw unavailable();
        }
        return new Actor(actor.id(), actor.username(), Set.copyOf(actor.roles()), Set.copyOf(actor.permissions()));
    }

    List<Room> rooms(Long buildingId, int limit, int offset) {
        String path = "/internal/v1/rooms?limit=" + limit + "&offset=" + offset
                + (buildingId == null ? "" : "&buildingId=" + buildingId);
        List<Room> rows = get(path, serviceToken, new ParameterizedTypeReference<Envelope<List<Room>>>() { }, false);
        if (rows.size() > limit) { throw unavailable(); }
        long last = 0;
        for (Room room : rows) {
            validate(room);
            if (!room.enabled() || room.id() <= last || (buildingId != null && room.buildingId() != buildingId)) {
                throw unavailable();
            }
            last = room.id();
        }
        return rows;
    }

    Room room(long id) {
        Room room = get("/internal/v1/rooms/" + id, serviceToken,
                new ParameterizedTypeReference<Envelope<Room>>() { }, false);
        validate(room);
        if (room.id() != id) { throw unavailable(); }
        return room;
    }

    List<Seat> seats(long roomId, Boolean hasPowerOutlet, int limit, int offset) {
        return seats(roomId, null, hasPowerOutlet, limit, offset);
    }

    Seat seat(long roomId, long seatId) {
        List<Seat> rows = seats(roomId, seatId, null, 1, 0);
        if (rows.isEmpty()) {
            throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "RESOURCE_UNAVAILABLE", "房间或座位当前不可预约");
        }
        return rows.getFirst();
    }

    Settings settings() {
        Settings settings = get("/internal/v1/settings", serviceToken,
                new ParameterizedTypeReference<Envelope<Settings>>() { }, false);
        if (settings.maxReservationHours() <= 0) { throw unavailable(); }
        return settings;
    }

    private List<Seat> seats(long roomId, Long seatId, Boolean power, int limit, int offset) {
        String path = "/internal/v1/rooms/" + roomId + "/seats?enabledOnly=true&limit=" + limit
                + "&offset=" + offset + (seatId == null ? "" : "&seatId=" + seatId)
                + (power == null ? "" : "&hasPowerOutlet=" + power);
        List<Seat> rows = get(path, serviceToken,
                new ParameterizedTypeReference<Envelope<List<Seat>>>() { }, false);
        if (rows.size() > limit) { throw unavailable(); }
        long last = 0;
        for (Seat seat : rows) {
            if (seat == null || seat.id() <= last || seat.roomId() != roomId || !seat.enabled()
                    || seat.code() == null || seat.code().isBlank() || seat.code().length() > 64
                    || (seatId != null && seat.id() != seatId) || (power != null && seat.hasPowerOutlet() != power)) {
                throw unavailable();
            }
            last = seat.id();
        }
        return rows;
    }

    private void validate(Room room) {
        if (room == null || room.id() <= 0 || room.buildingId() <= 0
                || room.code() == null || room.code().isBlank() || room.code().length() > 64
                || room.name() == null || room.name().isBlank() || room.name().length() > 100
                || room.type() == null || !Set.of("STUDY_ROOM", "CLASSROOM").contains(room.type())) {
            throw unavailable();
        }
    }

    private <T> T get(String path, String credential,
            ParameterizedTypeReference<Envelope<T>> type, boolean userIdentity) {
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
