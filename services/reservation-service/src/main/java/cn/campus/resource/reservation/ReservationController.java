package cn.campus.resource.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class ReservationController {
    record CreateReservation(@NotNull @Positive Long roomId, @NotNull @Positive Long seatId,
            @NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) { }
    record Rebook(@NotNull OffsetDateTime startAt, @NotNull OffsetDateTime endAt) { }

    private final ReservationService service;

    ReservationController(ReservationService service) { this.service = service; }

    @GetMapping("/api/v1/reservation/rooms")
    ApiEnvelope<List<PlatformClient.Room>> rooms(@RequestParam(required = false) @Positive Long buildingId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(service.rooms(buildingId, limit, offset));
    }

    @GetMapping("/api/v1/reservation/rooms/{id}/seats")
    ApiEnvelope<List<PlatformClient.Seat>> seats(@PathVariable @Positive long id,
            @RequestParam(required = false) Boolean hasPowerOutlet,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(service.seats(id, hasPowerOutlet, limit, offset));
    }

    @GetMapping("/api/v1/reservation/rooms/{id}/availability")
    ApiEnvelope<ReservationService.Availability> availability(@PathVariable @Positive long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endAt,
            @RequestParam(required = false) Boolean hasPowerOutlet,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(service.availability(id, startAt, endAt, hasPowerOutlet, limit, offset));
    }

    @PostMapping("/api/v1/reservations")
    ResponseEntity<ApiEnvelope<ReservationMapper.Booking>> create(@Valid @RequestBody CreateReservation input) {
        var reservation = service.create(input.roomId(), input.seatId(), input.startAt(), input.endAt());
        return created(reservation);
    }

    @GetMapping("/api/v1/reservations")
    ApiEnvelope<List<ReservationMapper.Booking>> list(
            @RequestParam(defaultValue = "current") @Pattern(regexp = "current|history") String view,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(service.list(view, limit, offset));
    }

    @GetMapping("/api/v1/reservations/{id}")
    ApiEnvelope<ReservationMapper.Booking> detail(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(service.detail(id));
    }

    @PostMapping("/api/v1/reservations/{id}/cancel")
    ApiEnvelope<ReservationMapper.Booking> cancel(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(service.cancel(id));
    }

    @PostMapping("/api/v1/reservations/{id}/rebook")
    ResponseEntity<ApiEnvelope<ReservationMapper.Booking>> rebook(@PathVariable @Positive long id,
            @Valid @RequestBody Rebook input) {
        return created(service.rebook(id, input.startAt(), input.endAt()));
    }

    private ResponseEntity<ApiEnvelope<ReservationMapper.Booking>> created(ReservationMapper.Booking reservation) {
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + reservation.id()))
                .body(ApiEnvelope.ok(reservation));
    }
}
