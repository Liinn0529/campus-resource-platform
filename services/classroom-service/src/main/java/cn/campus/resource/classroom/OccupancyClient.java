package cn.campus.resource.classroom;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class OccupancyClient {
    private record Block(long roomId, Instant startAt, Instant endAt, String sourceModule,
            String sourceType, String sourceRefId) { }
    private record Receipt(long id, long roomId, Instant startAt, Instant endAt, String sourceModule,
            String sourceType, String sourceRefId, String status) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Envelope(Receipt data, String requestId) { }
    static final class SyncFailure extends RuntimeException {
        private final boolean retryable;
        private final String code;
        SyncFailure(boolean retryable, String code) {
            super("Occupancy synchronization failed: " + code);
            this.retryable = retryable;
            this.code = code;
        }
        boolean retryable() { return retryable; }
        String code() { return code; }
    }
    private final RestClient http;
    private final String token;

    OccupancyClient(RestClient.Builder builder, @Value("${campus.reservation.base-url}") String base,
            @Value("${campus.reservation.service-token}") String token,
            @Value("${campus.platform.service-token}") String platformToken) {
        URI uri = URI.create(base);
        if (uri.getScheme() == null || !Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/")) || token.isBlank()
                || token.getBytes(StandardCharsets.UTF_8).length < 32 || token.equals(platformToken)) {
            throw new IllegalStateException("Configure an M1 origin and independent private M2 credential");
        }
        this.token = token;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.baseUrl(base).requestFactory(factory).build();
    }

    long create(ClassroomMapper.RequestRow request) { return synchronize(request, false); }
    long release(ClassroomMapper.RequestRow request) { return synchronize(request, true); }

    private long synchronize(ClassroomMapper.RequestRow request, boolean release) {
        String source = "classroom-request:" + request.id();
        try {
            var call = release ? http.method(org.springframework.http.HttpMethod.DELETE)
                    .uri("/internal/v1/room-blocks/M2/" + source) : http.post().uri("/internal/v1/room-blocks");
            call.headers(headers -> headers.setBearerAuth(token));
            if (!release) { call.body(new Block(request.roomId(), request.startAt(), request.endAt(), "M2", "CLASSROOM_BOOKING", source)); }
            var response = call.retrieve().toEntity(new ParameterizedTypeReference<Envelope>() { });
            var body = response.getBody();
            if ((response.getStatusCode().value() != 200 && (release || response.getStatusCode().value() != 201))
                    || body == null || body.data() == null || body.requestId() == null
                    || body.requestId().isBlank() || body.requestId().length() > 64) {
                throw new SyncFailure(false, "M1_INVALID_RECEIPT");
            }
            var row = body.data();
            if (row.id() <= 0 || row.roomId() != request.roomId() || !request.startAt().equals(row.startAt())
                    || !request.endAt().equals(row.endAt()) || !"M2".equals(row.sourceModule())
                    || !"CLASSROOM_BOOKING".equals(row.sourceType()) || !source.equals(row.sourceRefId())
                    || !(release ? "RELEASED" : "ACTIVE").equals(row.status())
                    || (release && !Long.valueOf(row.id()).equals(request.occupancyId()))) {
                throw new SyncFailure(false, "M1_INVALID_RECEIPT");
            }
            return row.id();
        } catch (RestClientResponseException failure) {
            throw new SyncFailure(failure.getStatusCode().is5xxServerError(), "M1_HTTP_" + failure.getStatusCode().value());
        } catch (ResourceAccessException failure) {
            throw new SyncFailure(true, "M1_NETWORK_ERROR");
        } catch (RestClientException failure) {
            throw new SyncFailure(false, "M1_INVALID_RECEIPT");
        }
    }
}
