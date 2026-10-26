package cn.campus.resource.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class LifecycleController {
    record CheckinInput(@NotNull @Pattern(regexp = "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}") String code) {
        @Override public String toString() { return "CheckinInput[code=redacted]"; }
    }

    private final LifecycleService lifecycle;

    LifecycleController(LifecycleService lifecycle) { this.lifecycle = lifecycle; }

    @PostMapping("/api/v1/reservations/{id}/checkin")
    ApiEnvelope<ReservationMapper.Booking> checkin(@PathVariable @Positive long id,
            @Valid @RequestBody CheckinInput input) {
        return ApiEnvelope.ok(lifecycle.checkin(id, input.code()));
    }

    @GetMapping("/api/v1/violations")
    ApiEnvelope<List<LifecycleMapper.Violation>> violations(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(lifecycle.violations(limit, offset));
    }
}
