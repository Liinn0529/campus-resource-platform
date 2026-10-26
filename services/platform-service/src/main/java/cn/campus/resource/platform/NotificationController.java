package cn.campus.resource.platform;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class NotificationController {
    record NotificationWrite(
            @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9:_-]{0,63}") String sourceRefId,
            @NotNull @Positive Long userId, @NotNull @Positive Long reservationId,
            @NotNull @Positive Long roomId, @NotNull OffsetDateTime startAt,
            @NotNull @Pattern(regexp = "CHECKIN_FIRST|CHECKIN_SECOND") String kind,
            @NotNull OffsetDateTime dueAt, @NotNull OffsetDateTime expiresAt) {
        @JsonAnySetter
        void rejectUnknown(String field, Object value) {
            throw new IllegalArgumentException("Unknown notification field");
        }
    }

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @PostMapping("/internal/v1/notifications")
    ResponseEntity<ApiEnvelope<NotificationMapper.Notification>> accept(
            @Valid @RequestBody NotificationWrite input) {
        var result = notifications.accept(input);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiEnvelope.ok(result.notification()));
    }

    @GetMapping("/api/v1/notifications")
    ApiEnvelope<List<NotificationMapper.Notification>> own(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(notifications.own(limit, offset));
    }
}
