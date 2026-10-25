package cn.campus.resource.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class SpaceController {
    private static final String CODE = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}";

    enum RoomType { STUDY_ROOM, CLASSROOM }

    record BuildingWrite(@NotBlank @Pattern(regexp = CODE) String code,
            @NotBlank @Size(max = 100) String name) { }

    record RoomCreate(@NotNull @Positive Long buildingId, @NotBlank @Pattern(regexp = CODE) String code,
            @NotBlank @Size(max = 100) String name, @NotNull RoomType type, @NotNull Boolean enabled) { }

    record RoomPatch(@Positive Long buildingId, @Pattern(regexp = CODE) String code,
            @Pattern(regexp = "(?s).*\\S.*") @Size(max = 100) String name,
            RoomType type, Boolean enabled) { }

    record SeatCreate(@NotBlank @Pattern(regexp = CODE) String code,
            @NotNull Boolean hasPowerOutlet, @NotNull Boolean enabled) { }

    record SeatPatch(@Positive Long roomId, @Pattern(regexp = CODE) String code,
            Boolean hasPowerOutlet, Boolean enabled) { }

    private final SpaceService spaces;

    SpaceController(SpaceService spaces) {
        this.spaces = spaces;
    }

    @GetMapping("/api/v1/buildings")
    ApiEnvelope<List<SpaceMapper.Building>> buildings(
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(spaces.buildings(limit, offset));
    }

    @PostMapping("/api/v1/buildings")
    ResponseEntity<ApiEnvelope<SpaceMapper.Building>> createBuilding(@Valid @RequestBody BuildingWrite input) {
        var building = spaces.createBuilding(input);
        return ResponseEntity.created(URI.create("/api/v1/buildings/" + building.id()))
                .body(ApiEnvelope.ok(building));
    }

    @PutMapping("/api/v1/buildings/{id}")
    ApiEnvelope<SpaceMapper.Building> updateBuilding(@PathVariable @Positive long id,
            @Valid @RequestBody BuildingWrite input) {
        return ApiEnvelope.ok(spaces.updateBuilding(id, input));
    }

    @GetMapping("/api/v1/rooms")
    ApiEnvelope<List<SpaceMapper.Room>> rooms(@RequestParam(required = false) @Positive Long buildingId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(spaces.rooms(buildingId, limit, offset));
    }

    @GetMapping("/api/v1/managed-rooms")
    ApiEnvelope<List<SpaceMapper.Room>> managedRooms(@RequestParam(required = false) @Positive Long buildingId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(spaces.managedRooms(buildingId, limit, offset));
    }

    @GetMapping("/api/v1/rooms/{id}")
    ApiEnvelope<SpaceMapper.Room> room(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(spaces.room(id));
    }

    @PostMapping("/api/v1/rooms")
    ResponseEntity<ApiEnvelope<SpaceMapper.Room>> createRoom(@Valid @RequestBody RoomCreate input) {
        var room = spaces.createRoom(input);
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + room.id())).body(ApiEnvelope.ok(room));
    }

    @PatchMapping("/api/v1/rooms/{id}")
    ApiEnvelope<SpaceMapper.Room> updateRoom(@PathVariable @Positive long id,
            @Valid @RequestBody RoomPatch input) {
        return ApiEnvelope.ok(spaces.updateRoom(id, input));
    }

    @GetMapping("/api/v1/rooms/{roomId}/seats")
    ApiEnvelope<List<SpaceMapper.Seat>> seats(@PathVariable @Positive long roomId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(spaces.seats(roomId, limit, offset));
    }

    @PostMapping("/api/v1/rooms/{roomId}/seats")
    ResponseEntity<ApiEnvelope<SpaceMapper.Seat>> createSeat(@PathVariable @Positive long roomId,
            @Valid @RequestBody SeatCreate input) {
        var seat = spaces.createSeat(roomId, input);
        return ResponseEntity.created(URI.create("/api/v1/seats/" + seat.id())).body(ApiEnvelope.ok(seat));
    }

    @PatchMapping("/api/v1/seats/{id}")
    ApiEnvelope<SpaceMapper.Seat> updateSeat(@PathVariable @Positive long id,
            @Valid @RequestBody SeatPatch input) {
        return ApiEnvelope.ok(spaces.updateSeat(id, input));
    }

    @GetMapping("/internal/v1/rooms")
    ApiEnvelope<List<SpaceMapper.Room>> internalRooms(
            @RequestParam(required = false) @Positive Long buildingId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(spaces.internalRooms(buildingId, limit, offset));
    }

    @GetMapping("/internal/v1/rooms/{id}")
    ApiEnvelope<SpaceMapper.Room> internalRoom(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(spaces.internalRoom(id));
    }

    @GetMapping("/internal/v1/rooms/{roomId}/seats")
    ApiEnvelope<List<SpaceMapper.Seat>> internalSeats(@PathVariable @Positive long roomId,
            @RequestParam(defaultValue = "false") boolean enabledOnly,
            @RequestParam(required = false) Boolean hasPowerOutlet,
            @RequestParam(required = false) @Positive Long seatId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(spaces.internalSeats(roomId, enabledOnly, hasPowerOutlet, seatId, limit, offset));
    }
}
