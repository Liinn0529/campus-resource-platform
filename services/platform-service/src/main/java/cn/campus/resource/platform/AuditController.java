package cn.campus.resource.platform;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class AuditController {
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
}
