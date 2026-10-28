package cn.campus.resource.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class RoomBlockTest extends ReservationApiTestSupport {
    private static final String M2 = "test-only-m1-classroom-client-token-at-least-thirty-two-bytes";
    private static final String M3 = "test-only-m1-maintenance-client-token-at-least-thirty-two-bytes";
    private static final String PATH = "/internal/v1/room-blocks";

    @BeforeEach void clean() { clearReservationRows(); }
    @AfterEach void cleanup() { clearReservationRows(); }

    @Test void bindsServiceIdentityAndRejectsUserTokensBeforeWriting() {
        var input = input(room(), "M2", "classroom-request:1", 1, 2);
        status(post(input, null), 401);
        status(post(input, login("demo_student_01")), 401);
        status(post(input, RESERVATION_TOKEN), 401);
        status(post(input, M3), 403);
        input.put("sourceType", "MAINTENANCE");
        status(post(input, M2), 403);
        status(request(HttpMethod.DELETE, PATH + "/M3/maintenance-ticket:1", null, M2), 403);
        assertThat(count()).isZero();
    }

    @Test void replaysEquivalentInstantsAndReleasedSourcesWithoutReactivation() {
        var input = input(room(), "M2", "classroom-request:1", 1, 2);
        var created = post(input, M2);
        status(created, 201);
        input.put("startAt", "2026-11-01T09:00:00+08:00");
        var replay = post(input, M2);
        status(replay, 200);
        assertThat(data(replay)).isEqualTo(data(created));
        var released = release("M2", "classroom-request:1", M2);
        status(released, 200);
        assertThat(data(released)).containsEntry("status", "RELEASED");
        assertThat(data(release("M2", "classroom-request:1", M2))).isEqualTo(data(released));
        assertThat(data(post(input, M2))).isEqualTo(data(released));
        input.put("endAt", at(3));
        status(post(input, M2), 409);
        assertThat(count()).isEqualTo(1);
        var absent = release("M2", "classroom-request:999", M2);
        status(absent, 404);
        assertThat(absent.getBody()).containsEntry("code", "OCCUPANCY_SOURCE_NOT_FOUND");
    }

    @Test void blocksEverySeatAllowsAdjacentIntervalsAndReleasesOnlyItsOwnSource() {
        long room = room();
        String student = login("demo_student_01");
        status(post(input(room, "M2", "classroom-request:1", 1, 2), M2), 201);
        for (long seat : platformJdbc.queryForList("SELECT id FROM platform.seat WHERE room_id = ?", Long.class, room)) {
            status(reserve(room, seat, 1, 2, student), 409);
        }
        status(post(input(room, "M2", "classroom-request:2", 2, 3), M2), 201);
        status(post(input(room, "M3", "maintenance-ticket:1", 2, null), M3), 409);
        status(release("M2", "classroom-request:1", M2), 200);
        long seat = platformJdbc.queryForObject("SELECT min(id) FROM platform.seat WHERE room_id = ?", Long.class, room);
        status(reserve(room, seat, 1, 2, student), 201);
        status(reserve(room, seat, 2, 3, student), 409);
    }

    @Test void maintenanceOpenIntervalConflictsWithExistingBookingsAndRestoresAvailability() {
        long room = room();
        long seat = platformJdbc.queryForObject("SELECT min(id) FROM platform.seat WHERE room_id = ?", Long.class, room);
        String student = login("demo_student_01");
        var booking = reserve(room, seat, 3, 4, student);
        status(booking, 201);
        status(post(input(room, "M3", "maintenance-ticket:1", 1, null), M3), 409);
        status(request(HttpMethod.POST, "/api/v1/reservations/" + data(booking).get("id") + "/cancel", null, student), 200);
        status(post(input(room, "M3", "maintenance-ticket:1", 1, null), M3), 201);
        status(reserve(room, seat, 4, 5, student), 409);
        status(release("M3", "maintenance-ticket:1", M3), 200);
        status(reserve(room, seat, 4, 5, student), 201);
    }

    @Test void validatesTimeSourceAndResources() {
        var input = input(room(), "M2", "x", 1, null);
        status(post(input, M2), 400);
        input.put("endAt", at(1)); status(post(input, M2), 400);
        input.put("endAt", at(2)); input.put("roomId", -1); status(post(input, M2), 400);
        input.put("roomId", Long.MAX_VALUE); status(post(input, M2), 404);
        input.put("roomId", room()); input.put("startAt", "2026-11-01T01:00:00.000000001Z");
        status(post(input, M2), 400);
        status(post(input(room(), "M3", "x", 1, 2), M3), 400);
    }

    @Test void disablingAResourceRejectsNewSourcesButDoesNotPreventRecoveryOfOldSources() {
        long room = room();
        var input = input(room, "M2", "classroom-request:disabled", 1, 2);
        status(post(input, M2), 201);
        platformJdbc.update("UPDATE platform.room SET enabled=false WHERE id=?", room);
        try {
            status(post(input, M2), 200);
            status(post(input(room, "M2", "classroom-request:new", 2, 3), M2), 422);
            status(release("M2", "classroom-request:disabled", M2), 200);
            assertThat(data(post(input, M2))).containsEntry("status", "RELEASED");
        } finally { platformJdbc.update("UPDATE platform.room SET enabled=true WHERE id=?", room); }
    }

    @Test void concurrentFirstCreationOfSameSourceHasOneCreatedAndIdenticalReplays() throws Exception {
        long room = room();
        var statuses = concurrent(6, i -> post(input(room, "M2", "classroom-request:1", 1, 2), M2));
        assertThat(statuses).containsOnly(200, 201);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(count()).isEqualTo(1);
    }

    @Test void concurrentDifferentRoomsCannotReuseTheSameSource() throws Exception {
        List<Long> rooms = platformJdbc.queryForList("SELECT id FROM platform.room WHERE enabled ORDER BY id", Long.class);
        var statuses = concurrent(2, i -> post(input(rooms.get(i), "M3", "maintenance-ticket:1", 1, null), M3));
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(count()).isEqualTo(1);
    }

    @Test void firstRoomLockSerializesSeatClassroomAndMaintenanceRequests() throws Exception {
        long room = room();
        long seat = platformJdbc.queryForObject("SELECT min(id) FROM platform.seat WHERE room_id = ?", Long.class, room);
        String student = login("demo_student_01");
        var statuses = concurrent(3, i -> i == 0 ? reserve(room, seat, 1, 2, student)
                : post(input(room, i == 1 ? "M2" : "M3", "race:" + i, 1, i == 1 ? 2 : null), i == 1 ? M2 : M3));
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(2);
        assertThat(count()).isEqualTo(1);
    }

    private List<Integer> concurrent(int n, java.util.function.IntFunction<ResponseEntity<Map>> call) throws Exception {
        var ready = new CountDownLatch(n); var start = new CountDownLatch(1);
        var statuses = new ArrayList<Integer>();
        try (var pool = Executors.newFixedThreadPool(n)) {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("start timeout"); }
                    return call.apply(index).getStatusCode().value();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (var future : futures) { statuses.add(future.get(30, TimeUnit.SECONDS)); }
        }
        return statuses;
    }

    private long room() { return platformJdbc.queryForObject("SELECT min(id) FROM platform.room WHERE type = 'CLASSROOM' AND enabled", Long.class); }
    private int count() { return jdbc.queryForObject("SELECT count(*) FROM reservation.seat_occupancy", Integer.class); }
    private String at(int hour) { return INITIAL_TIME.plusSeconds(hour * 3600L).toString(); }
    private Map<String, Object> input(long room, String module, String ref, int start, Integer end) {
        var result = new HashMap<String, Object>();
        result.put("roomId", room); result.put("sourceModule", module);
        result.put("sourceType", module.equals("M2") ? "CLASSROOM_BOOKING" : "MAINTENANCE");
        result.put("sourceRefId", ref); result.put("startAt", at(start)); result.put("endAt", end == null ? null : at(end));
        return result;
    }
    private ResponseEntity<Map> post(Object body, String token) { return request(HttpMethod.POST, PATH, body, token); }
    private ResponseEntity<Map> release(String module, String ref, String token) { return request(HttpMethod.DELETE, PATH + "/" + module + "/" + ref, null, token); }
    private ResponseEntity<Map> reserve(long room, long seat, int start, int end, String token) {
        return request(HttpMethod.POST, "/api/v1/reservations", Map.of("roomId", room, "seatId", seat, "startAt", at(start), "endAt", at(end)), token);
    }
    private void status(ResponseEntity<Map> response, int expected) { assertThat(response.getStatusCode().value()).as("%s", response.getBody()).isEqualTo(expected); }
}
