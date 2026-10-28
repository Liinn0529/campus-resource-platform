package cn.campus.resource.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class AuditController {
    record ExternalAuditEvent(@NotNull @Pattern(regexp = "M1|M2|M3") String sourceModule,
            @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9:_-]{0,63}") String sourceEventId,
            @NotNull @Pattern(regexp = "[A-Z_]{1,64}") String action,
            @NotNull @Pattern(regexp = "RESERVATION|CLASSROOM_REQUEST|MAINTENANCE_TICKET") String entityType,
            @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9:_-]{0,63}") String entityId,
            @Positive Long actorUserId, @NotNull OffsetDateTime occurredAt,
            @NotNull UUID sourceRequestId) { }
    record AuditReceipt(long id, String status) { }

    private static final Map<String, String> PRODUCERS = Map.of(
            "M1", "reservation-service", "M2", "classroom-service", "M3", "maintenance-service");
    private static final Map<String, Set<String>> ALLOWED_ACTIONS = Map.of(
            "M1", Set.of("CREATED", "REBOOKED", "CANCELLED", "CHECKED_IN", "NO_SHOW"),
            "M2", Set.of("SUBMITTED", "REJECTED", "FIRST_APPROVED", "FINAL_APPROVED", "CANCELLED",
                    "AUTO_CANCELLED", "SYNC_FAILED", "SYNC_ATTEMPTED", "OCCUPANCY_CONFIRMED",
                    "SYNC_RETRY_SCHEDULED"),
            "M3", Set.of("REPORTED", "ASSIGNED", "START_REQUESTED", "FINISHED", "ACCEPT_REQUESTED",
                    "ACCEPTANCE_REJECTED", "CANCELLED", "REVIEWED", "ESCALATED", "SYNC_FAILED",
                    "SYNC_ATTEMPTED", "OCCUPANCY_CONFIRMED", "RELEASE_CONFIRMED", "SYNC_RETRY_SCHEDULED"));
    private final AuditService service;

    AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/audit-events")
    ApiEnvelope<?> events(@RequestParam(required = false) @Pattern(regexp = "[A-Z_]{1,32}") String targetType,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(service.events(targetType, limit, offset));
    }

    @PostMapping("/internal/v1/audit-events")
    ResponseEntity<ApiEnvelope<AuditReceipt>> receive(@Valid @RequestBody ExternalAuditEvent input) {
        String expectedProducer = PRODUCERS.get(input.sourceModule());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (expectedProducer == null || authentication == null
                || !expectedProducer.equals(authentication.getPrincipal())) {
            throw new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "服务身份与审计来源不匹配");
        }
        String expectedType = switch (input.sourceModule()) {
            case "M1" -> "RESERVATION";
            case "M2" -> "CLASSROOM_REQUEST";
            case "M3" -> "MAINTENANCE_TICKET";
            default -> throw new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "审计来源无效");
        };
        if (!expectedType.equals(input.entityType())
                || !ALLOWED_ACTIONS.get(input.sourceModule()).contains(input.action())) {
            throw new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIT_EVENT_NOT_ALLOWED",
                    "审计动作或目标类型不在来源白名单内");
        }
        var result = service.receive(expectedProducer, input.sourceEventId(), input.action(), input.entityType(),
                input.entityId(), input.actorUserId(), input.occurredAt().toInstant(), input.sourceRequestId());
        var receipt = new AuditReceipt(result.id(), "ACCEPTED");
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiEnvelope.ok(receipt));
    }
}
