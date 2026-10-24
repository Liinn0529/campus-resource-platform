package cn.campus.resource.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class CommonController {
    record SettingsWrite(@NotNull @Positive Integer maxReservationHours) { }
    record CodeVerification(@NotNull @Positive Long roomId,
            @NotNull @Pattern(regexp = "[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}") String code) { }

    private final CommonService common;

    CommonController(CommonService common) {
        this.common = common;
    }

    @GetMapping("/api/v1/settings")
    ApiEnvelope<CommonService.Settings> settings() {
        return ApiEnvelope.ok(common.settings());
    }

    @PutMapping("/api/v1/settings")
    ApiEnvelope<CommonService.Settings> updateSettings(@Valid @RequestBody SettingsWrite input) {
        return ApiEnvelope.ok(common.updateSettings(input));
    }

    @GetMapping("/api/v1/rooms/{id}/checkin-code")
    ApiEnvelope<CommonService.CheckinCode> checkinCode(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(common.checkinCode(id));
    }

    @PostMapping("/api/v1/rooms/{id}/checkin-code")
    ApiEnvelope<CommonService.CheckinCode> ensureCheckinCode(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(common.ensureCheckinCode(id));
    }

    @GetMapping("/internal/v1/settings")
    ApiEnvelope<CommonService.Settings> internalSettings() {
        return ApiEnvelope.ok(common.internalSettings());
    }

    @PostMapping("/internal/v1/checkin-codes/verify")
    ApiEnvelope<CommonService.Verification> verify(@Valid @RequestBody CodeVerification input) {
        return ApiEnvelope.ok(common.verify(input));
    }
}
