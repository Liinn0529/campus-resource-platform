package cn.campus.resource.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class RoomBlockController {
    record Input(@NotNull @Positive Long roomId, @NotNull OffsetDateTime startAt, OffsetDateTime endAt,
            @NotNull @Pattern(regexp = "M2|M3") String sourceModule,
            @NotNull @Pattern(regexp = "CLASSROOM_BOOKING|MAINTENANCE") String sourceType,
            @NotNull @Pattern(regexp = "[A-Za-z0-9:_-]{1,64}") String sourceRefId) { }

    private final RoomBlockMapper blocks;
    private final ReservationMapper reservations;
    private final PlatformClient platform;
    private final Clock clock;
    private final TransactionTemplate transactions;

    RoomBlockController(RoomBlockMapper blocks, ReservationMapper reservations, PlatformClient platform,
            Clock clock, PlatformTransactionManager manager) {
        this.blocks = blocks;
        this.reservations = reservations;
        this.platform = platform;
        this.clock = clock;
        transactions = new TransactionTemplate(manager);
        transactions.setTimeout(10);
    }

    @PostMapping("/internal/v1/room-blocks")
    ResponseEntity<ApiEnvelope<RoomBlockMapper.Block>> create(@Valid @RequestBody Input input, Authentication caller) {
        String type = authorize(input.sourceModule(), caller);
        if (!type.equals(input.sourceType())) { throw forbidden(); }
        if ((input.sourceModule().equals("M2") && input.endAt() == null)
                || (input.sourceModule().equals("M3") && input.endAt() != null)
                || (input.endAt() != null && !input.endAt().toInstant().isAfter(input.startAt().toInstant()))
                || input.startAt().getNano() % 1000 != 0
                || (input.endAt() != null && input.endAt().getNano() % 1000 != 0)) {
            throw new ApiFailure(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "占用时间或精度不符合契约");
        }
        // Replays remain recoverable even if D is unavailable or the resource was later disabled.
        var previous = blocks.source(input.sourceModule(), type, input.sourceRefId());
        if (previous == null) {
            var room = platform.room(input.roomId());
            if (!room.enabled() || (input.sourceModule().equals("M2") && !room.type().equals("CLASSROOM"))) {
                throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "RESOURCE_UNAVAILABLE", "房间不可用于该占用");
            }
        }
        return transactions.execute(tx -> {
            lockRoom(input.roomId());
            var existing = blocks.source(input.sourceModule(), type, input.sourceRefId());
            if (existing != null) { return replay(existing, input); }
            if (blocks.conflict(input.roomId(), input.startAt().toInstant(),
                    input.endAt() == null ? null : input.endAt().toInstant())) {
                throw new ApiFailure(HttpStatus.CONFLICT, "OCCUPANCY_TIME_CONFLICT", "房间时段已有占用");
            }
            var created = blocks.insert(input.roomId(), input.startAt().toInstant(),
                    input.endAt() == null ? null : input.endAt().toInstant(), input.sourceModule(), type,
                    input.sourceRefId(), clock.instant());
            // The unique source also protects two concurrent requests naming different rooms.
            if (created == null) {
                return replay(blocks.source(input.sourceModule(), type, input.sourceRefId()), input);
            }
            return ResponseEntity.status(201).body(ApiEnvelope.ok(created));
        });
    }

    @DeleteMapping("/internal/v1/room-blocks/{sourceModule}/{sourceRefId}")
    ApiEnvelope<RoomBlockMapper.Block> release(@PathVariable @Pattern(regexp = "M2|M3") String sourceModule,
            @PathVariable @Pattern(regexp = "[A-Za-z0-9:_-]{1,64}") String sourceRefId, Authentication caller) {
        String type = authorize(sourceModule, caller);
        var previous = blocks.source(sourceModule, type, sourceRefId);
        if (previous == null) {
            throw new ApiFailure(HttpStatus.NOT_FOUND, "OCCUPANCY_SOURCE_NOT_FOUND", "占用来源不存在");
        }
        return transactions.execute(tx -> {
            lockRoom(previous.roomId());
            var current = blocks.source(sourceModule, type, sourceRefId);
            if (current.status().equals("ACTIVE") && blocks.release(current.id(), clock.instant()) != 1) {
                throw new IllegalStateException("Occupancy release lost its room lock");
            }
            return ApiEnvelope.ok(blocks.source(sourceModule, type, sourceRefId));
        });
    }

    private ResponseEntity<ApiEnvelope<RoomBlockMapper.Block>> replay(RoomBlockMapper.Block row, Input input) {
        if (row.roomId() != input.roomId() || !row.startAt().equals(input.startAt().toInstant())
                || !Objects.equals(row.endAt(), input.endAt() == null ? null : input.endAt().toInstant())) {
            throw new ApiFailure(HttpStatus.CONFLICT, "OCCUPANCY_SOURCE_CONFLICT", "同一来源的内容不能改变");
        }
        return ResponseEntity.ok(ApiEnvelope.ok(row));
    }

    private String authorize(String module, Authentication caller) {
        if (!module.equals(caller.getName())) { throw forbidden(); }
        return module.equals("M2") ? "CLASSROOM_BOOKING" : "MAINTENANCE";
    }

    private ApiFailure forbidden() {
        return new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "调用方不能操作该来源");
    }

    private void lockRoom(long id) {
        reservations.ensureGate(id);
        if (reservations.lockGate(id) == null) { throw new IllegalStateException("Room gate missing"); }
    }
}
