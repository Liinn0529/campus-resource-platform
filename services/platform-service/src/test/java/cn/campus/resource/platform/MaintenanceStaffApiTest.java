package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class MaintenanceStaffApiTest extends ApiTestSupport {
    @Test
    void onlyMaintenanceCredentialCanReadMinimalCurrentEligibility() {
        long id = userId("demo_property_staff");
        var response = request(HttpMethod.GET, "/internal/v1/maintenance-staff/" + id, null, MAINTENANCE_TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map<?, ?>) response.getBody().get("data")).hasSize(2);
        assertThat(((Map<?, ?>) response.getBody().get("data")).get("eligible")).isEqualTo(true);
        for (String token : List.of(RESERVATION_TOKEN, CLASSROOM_TOKEN)) {
            assertThat(request(HttpMethod.GET, "/internal/v1/maintenance-staff/" + id, null, token).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(request(HttpMethod.GET, "/internal/v1/maintenance-staff/" + id, null, login("demo_admin")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void disabledRevokedUnknownAndOtherRolesAreNotEligible() {
        long id = userId("demo_property_staff");
        String admin = login("demo_admin");
        try {
            assertThat(eligible(id)).isTrue();
            assertThat(request(HttpMethod.PUT, "/api/v1/users/" + id + "/roles",
                    Map.of("roleCodes", List.of("STUDENT")), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(eligible(id)).isFalse();
        } finally {
            assertThat(request(HttpMethod.PUT, "/api/v1/users/" + id + "/roles",
                    Map.of("roleCodes", List.of("PROPERTY_STAFF")), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        try {
            jdbc.update("UPDATE platform.\"user\" SET enabled=false WHERE id=?", id);
            assertThat(eligible(id)).isFalse();
        } finally { jdbc.update("UPDATE platform.\"user\" SET enabled=true WHERE id=?", id); }
        assertThat(eligible(Long.MAX_VALUE)).isFalse();
        assertThat(eligible(userId("demo_admin"))).isFalse();
    }

    @Test
    void invalidIdsAndMissingServiceIdentityFail() {
        assertThat(request(HttpMethod.GET, "/internal/v1/maintenance-staff/0", null, MAINTENANCE_TOKEN).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(request(HttpMethod.GET, "/internal/v1/maintenance-staff/1", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private boolean eligible(long id) {
        var response = request(HttpMethod.GET, "/internal/v1/maintenance-staff/" + id, null, MAINTENANCE_TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Boolean) ((Map<?, ?>) response.getBody().get("data")).get("eligible");
    }
}
