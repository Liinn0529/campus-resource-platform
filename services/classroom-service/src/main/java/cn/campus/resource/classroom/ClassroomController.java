package cn.campus.resource.classroom;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
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
class ClassroomController {
    record Submit(@NotNull @Positive Long roomId, @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt, @NotBlank @Size(max = 500) String purpose) {
        @Override public String toString() { return "Submit[content=redacted]"; }
    }
    record Recovery(@NotBlank @Size(max=500) String reason) {
        @Override public String toString(){return "Recovery[reason=redacted]";}
    }
    record Decision(@Size(max = 500) String reason) {
        @Override public String toString() { return "Decision[reason=redacted]"; }
    }
    private final ClassroomService classroom;
    ClassroomController(ClassroomService classroom) { this.classroom = classroom; }

    @PostMapping("/api/v1/classroom-requests")
    ResponseEntity<ApiEnvelope<ClassroomService.View>> submit(@Valid @RequestBody Submit input) {
        var result = classroom.submit(input);
        return ResponseEntity.created(URI.create("/api/v1/classroom-requests/" + result.id())).body(ApiEnvelope.ok(result));
    }

    @GetMapping("/api/v1/classroom-requests")
    ApiEnvelope<List<ClassroomService.View>> list(
            @RequestParam(defaultValue = "mine") @Pattern(regexp = "mine|pending") String view,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(classroom.list(view, limit, offset));
    }

    @GetMapping("/api/v1/classroom-requests/{id}")
    ApiEnvelope<ClassroomService.Detail> detail(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(classroom.detail(id));
    }

    @PostMapping("/api/v1/classroom-requests/{id}/approve")
    ApiEnvelope<ClassroomService.View> approve(@PathVariable @Positive long id, @Valid @RequestBody Decision input) {
        return ApiEnvelope.ok(classroom.decide(id, true, input.reason()));
    }

    @PostMapping("/api/v1/classroom-requests/{id}/reject")
    ApiEnvelope<ClassroomService.View> reject(@PathVariable @Positive long id, @Valid @RequestBody Decision input) {
        return ApiEnvelope.ok(classroom.decide(id, false, input.reason()));
    }

    @PostMapping("/api/v1/classroom-requests/{id}/cancel")
    ApiEnvelope<ClassroomService.View> cancel(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(classroom.cancel(id));
    }

    @PostMapping("/api/v1/classroom-requests/{id}/recover")
    ApiEnvelope<ClassroomService.View> recover(@PathVariable @Positive long id,@Valid @RequestBody Recovery input) {
        return ApiEnvelope.ok(classroom.recover(id,input.reason()));
    }
}
