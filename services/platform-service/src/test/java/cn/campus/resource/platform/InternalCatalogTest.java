package cn.campus.resource.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class InternalCatalogTest extends ApiTestSupport {
    @Test
    void internalRoomCatalogRequiresAServiceIdentityAndPaginatesEnabledRooms() {
        long building = building();
        try {
            long first = room(building, "FIRST", "STUDY_ROOM", true);
            room(building, "DISABLED", "STUDY_ROOM", false);
            long second = room(building, "SECOND", "CLASSROOM", true);
            String path = "/internal/v1/rooms?buildingId=" + building + "&limit=1";
            for (String token : List.of(RESERVATION_TOKEN, CLASSROOM_TOKEN, MAINTENANCE_TOKEN)) {
                var response = internalRequest(HttpMethod.GET, path, null, token);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(ids(response)).containsExactly(first);
                assertThat(ids(internalRequest(HttpMethod.GET, path + "&offset=1", null, token)))
                        .containsExactly(second);
                assertThat(rows(internalRequest(HttpMethod.GET, path + "&offset=2", null, token))).isEmpty();
            }
            assertThat(internalRequest(HttpMethod.GET, path, null, null).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(internalRequest(HttpMethod.GET, path, null, login("demo_admin")).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        } finally {
            removeBuilding(building);
        }
    }

    @Test
    void seatFiltersApplyBeforePaginationAndPreserveTheLegacyUnfilteredContract() {
        long building = building();
        try {
            long room = room(building, "FILTER", "STUDY_ROOM", true);
            long disabledWithoutPower = seat(room, "S01", false, false);
            long withoutPower = seat(room, "S02", false, true);
            long disabledWithPower = seat(room, "S03", true, false);
            long firstWithPower = seat(room, "S04", true, true);
            long secondWithPower = seat(room, "S05", true, true);
            long lastWithoutPower = seat(room, "S06", false, true);
            String path = "/internal/v1/rooms/" + room + "/seats";
            assertThat(ids(read(path))).containsExactly(disabledWithoutPower, withoutPower,
                    disabledWithPower, firstWithPower, secondWithPower, lastWithoutPower);
            String filtered = path + "?enabledOnly=true&hasPowerOutlet=true&limit=1";
            assertThat(ids(read(filtered))).containsExactly(firstWithPower);
            assertThat(ids(read(filtered + "&offset=1"))).containsExactly(secondWithPower);
            assertThat(rows(read(filtered + "&offset=2"))).isEmpty();
            assertThat(ids(read(path + "?enabledOnly=true&hasPowerOutlet=false")))
                    .containsExactly(withoutPower, lastWithoutPower);
            assertThat(rows(read(path + "?enabledOnly=true&seatId=" + disabledWithPower))).isEmpty();
            assertThat(ids(read(path + "?enabledOnly=true&seatId=" + secondWithPower)))
                    .containsExactly(secondWithPower);
            assertThat(rows(read(path + "?seatId=" + secondWithPower + "&hasPowerOutlet=false"))).isEmpty();
            long otherRoom = room(building, "OTHER", "CLASSROOM", true);
            long otherSeat = seat(otherRoom, "S01", true, true);
            assertThat(rows(read(path + "?seatId=" + otherSeat))).isEmpty();

            jdbc.update("UPDATE platform.room SET enabled = FALSE WHERE id = ?", room);
            assertThat(rows(read(path + "?enabledOnly=true"))).isEmpty();
            assertThat(rows(read(path))).hasSize(6);
        } finally {
            removeBuilding(building);
        }
    }

    @Test
    void catalogRejectsInvalidFiltersAndReturnsOnlySpaceMetadata() {
        long room = jdbc.queryForObject("SELECT MIN(id) FROM platform.room", Long.class);
        String seats = "/internal/v1/rooms/" + room + "/seats";
        for (String path : List.of("/internal/v1/rooms?limit=101", "/internal/v1/rooms?buildingId=-1",
                seats + "?seatId=0", seats + "?offset=-1", seats + "?hasPowerOutlet=unknown",
                seats + "?enabledOnly=unknown")) {
            assertThat(read(path).getStatusCode()).as(path).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        var response = read(seats + "?enabledOnly=true&limit=1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rows(response)).hasSize(1);
        assertThat(rows(response).getFirst().keySet())
                .containsExactlyInAnyOrder("id", "roomId", "code", "hasPowerOutlet", "enabled");
        assertThat(response.getBody().get("requestId"))
                .isEqualTo(response.getHeaders().getFirst("X-Request-ID"));
    }

    private ResponseEntity<Map> read(String path) {
        return internalRequest(HttpMethod.GET, path, null, RESERVATION_TOKEN);
    }

    private long building() {
        return jdbc.queryForObject("INSERT INTO platform.building (code, name) VALUES (?, ?) RETURNING id",
                Long.class, "CATALOG_" + UUID.randomUUID().toString().replace("-", ""), "Catalog test building");
    }

    private long room(long building, String code, String type, boolean enabled) {
        return jdbc.queryForObject("""
                INSERT INTO platform.room (building_id, code, name, type, enabled)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, building, code, "Catalog test room", type, enabled);
    }

    private long seat(long room, String code, boolean power, boolean enabled) {
        return jdbc.queryForObject("""
                INSERT INTO platform.seat (room_id, code, has_power_outlet, enabled)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class, room, code, power, enabled);
    }

    private void removeBuilding(long building) {
        jdbc.update("DELETE FROM platform.seat WHERE room_id IN (SELECT id FROM platform.room WHERE building_id = ?)", building);
        jdbc.update("DELETE FROM platform.room WHERE building_id = ?", building);
        jdbc.update("DELETE FROM platform.building WHERE id = ?", building);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        return (List<Map<String, Object>>) response.getBody().get("data");
    }

    private List<Long> ids(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return rows(response).stream().map(row -> ((Number) row.get("id")).longValue()).toList();
    }
}
