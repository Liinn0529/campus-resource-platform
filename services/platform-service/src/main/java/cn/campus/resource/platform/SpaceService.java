package cn.campus.resource.platform;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SpaceService {
    private final SpaceMapper spaces;
    private final AccessPolicy access;
    private final AuditService audit;

    SpaceService(SpaceMapper spaces, AccessPolicy access, AuditService audit) {
        this.spaces = spaces;
        this.access = access;
        this.audit = audit;
    }

    List<SpaceMapper.Building> buildings(int limit, int offset) {
        access.require("space:view");
        return spaces.buildings(limit, offset);
    }

    List<SpaceMapper.Room> rooms(Long buildingId, int limit, int offset) {
        access.require("space:view");
        return spaces.rooms(buildingId, limit, offset);
    }

    List<SpaceMapper.Room> managedRooms(Long buildingId, int limit, int offset) {
        access.require("space:manage");
        var actor = access.actor();
        return spaces.managedRooms(actor.id(), actor.roles().contains("SYSTEM_ADMIN"),
                buildingId, limit, offset);
    }

    SpaceMapper.Room room(long id) {
        access.require("space:view");
        var room = requireRoom(id);
        if (!room.enabled() && !access.hasRoom(id)) {
            throw notFound("ROOM_NOT_FOUND", "Room not found");
        }
        return room;
    }

    List<SpaceMapper.Seat> seats(long roomId, int limit, int offset) {
        access.require("space:view");
        room(roomId);
        return spaces.seats(roomId, access.hasRoom(roomId), limit, offset);
    }

    @Transactional
    SpaceMapper.Building createBuilding(SpaceController.BuildingWrite input) {
        access.requireGlobal("space:manage");
        var result = spaces.insertBuilding(input.code().strip(), input.name().strip());
        audit.record("BUILDING_CREATE", "BUILDING", Long.toString(result.id()));
        return result;
    }

    @Transactional
    SpaceMapper.Building updateBuilding(long id, SpaceController.BuildingWrite input) {
        access.requireGlobal("space:manage");
        if (spaces.updateBuilding(id, input.code().strip(), input.name().strip()) == 0) {
            throw notFound("BUILDING_NOT_FOUND", "Building not found");
        }
        audit.record("BUILDING_UPDATE", "BUILDING", Long.toString(id));
        return spaces.building(id);
    }

    @Transactional
    SpaceMapper.Room createRoom(SpaceController.RoomCreate input) {
        access.requireGlobal("space:manage");
        requireBuilding(input.buildingId());
        var result = spaces.insertRoom(input.buildingId(), input.code().strip(), input.name().strip(),
                input.type().name(), input.enabled());
        audit.record("ROOM_CREATE", "ROOM", Long.toString(result.id()));
        return result;
    }

    @Transactional
    SpaceMapper.Room updateRoom(long id, SpaceController.RoomPatch input) {
        access.requireRoom(id, "space:manage");
        var previous = spaces.lockRoom(id);
        if (previous == null) {
            throw notFound("ROOM_NOT_FOUND", "Room not found");
        }
        if (input.buildingId() != null || input.code() != null || input.type() != null) {
            access.requireGlobal("space:manage");
        }
        if (input.buildingId() == null && input.code() == null && input.name() == null
                && input.type() == null && input.enabled() == null) {
            throw invalidPatch();
        }
        long buildingId = input.buildingId() == null ? previous.buildingId() : input.buildingId();
        requireBuilding(buildingId);
        var result = new SpaceMapper.Room(id, buildingId,
                input.code() == null ? previous.code() : input.code().strip(),
                input.name() == null ? previous.name() : input.name().strip(),
                input.type() == null ? previous.type() : input.type().name(),
                input.enabled() == null ? previous.enabled() : input.enabled());
        spaces.updateRoom(result);
        audit.record("ROOM_UPDATE", "ROOM", Long.toString(id));
        return result;
    }

    @Transactional
    SpaceMapper.Seat createSeat(long roomId, SpaceController.SeatCreate input) {
        access.requireRoom(roomId, "space:manage");
        requireRoom(roomId);
        var result = spaces.insertSeat(roomId, input.code().strip(), input.hasPowerOutlet(), input.enabled());
        audit.record("SEAT_CREATE", "SEAT", Long.toString(result.id()));
        return result;
    }

    @Transactional
    SpaceMapper.Seat updateSeat(long id, SpaceController.SeatPatch input) {
        access.require("space:manage");
        var previous = spaces.lockSeat(id);
        if (previous == null) {
            throw notFound("SEAT_NOT_FOUND", "Seat not found");
        }
        access.requireRoom(previous.roomId(), "space:manage");
        if (input.roomId() != null) {
            access.requireGlobal("space:manage");
            requireRoom(input.roomId());
        }
        if (input.roomId() == null && input.code() == null && input.hasPowerOutlet() == null
                && input.enabled() == null) {
            throw invalidPatch();
        }
        var result = new SpaceMapper.Seat(id,
                input.roomId() == null ? previous.roomId() : input.roomId(),
                input.code() == null ? previous.code() : input.code().strip(),
                input.hasPowerOutlet() == null ? previous.hasPowerOutlet() : input.hasPowerOutlet(),
                input.enabled() == null ? previous.enabled() : input.enabled());
        spaces.updateSeat(result);
        audit.record("SEAT_UPDATE", "SEAT", Long.toString(id));
        return result;
    }

    SpaceMapper.Room internalRoom(long id) {
        return requireRoom(id);
    }

    List<SpaceMapper.Seat> internalSeats(long roomId, int limit, int offset) {
        requireRoom(roomId);
        return spaces.seats(roomId, true, limit, offset);
    }

    private SpaceMapper.Room requireRoom(long id) {
        var room = spaces.room(id);
        if (room == null) {
            throw notFound("ROOM_NOT_FOUND", "Room not found");
        }
        return room;
    }

    private void requireBuilding(long id) {
        if (spaces.building(id) == null) {
            throw notFound("BUILDING_NOT_FOUND", "Building not found");
        }
    }

    private static ApiFailure invalidPatch() {
        return new ApiFailure(HttpStatus.BAD_REQUEST, "EMPTY_PATCH", "At least one field must be supplied");
    }

    private static ApiFailure notFound(String code, String message) {
        return new ApiFailure(HttpStatus.NOT_FOUND, code, message);
    }
}
