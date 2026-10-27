package cn.campus.resource.maintenance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
class MaintenanceController {
    record Report(@NotNull @Positive Long roomId, @NotBlank @Size(max=500) String description) {
        @Override public String toString() { return "Report[content=redacted]"; }
    }
    record Assignment(@NotNull @Positive Long assigneeId) { }
    record Note(@NotBlank @Size(max=500) String note) {
        @Override public String toString() { return "Note[content=redacted]"; }
    }
    record Rating(@NotNull @Min(1) @Max(5) Integer rating, @Size(max=500) String comment) {
        @Override public String toString() { return "Rating[content=redacted]"; }
    }
    private final MaintenanceService maintenance;
    MaintenanceController(MaintenanceService maintenance) { this.maintenance=maintenance; }

    @PostMapping("/api/v1/maintenance-tickets")
    ResponseEntity<ApiEnvelope<MaintenanceService.View>> report(@Valid @RequestBody Report input) {
        var result=maintenance.report(input);
        return ResponseEntity.created(URI.create("/api/v1/maintenance-tickets/"+result.id())).body(ApiEnvelope.ok(result));
    }
    @GetMapping("/api/v1/maintenance-tickets")
    ApiEnvelope<List<MaintenanceService.View>> list(
            @RequestParam(defaultValue="mine") @Pattern(regexp="mine|assigned|managed") String view,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue="0") @Min(0) int offset) {
        return ApiEnvelope.ok(maintenance.list(view,limit,offset));
    }
    @GetMapping("/api/v1/maintenance-tickets/{id}")
    ApiEnvelope<MaintenanceService.Detail> detail(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(maintenance.detail(id));
    }
    @PostMapping("/api/v1/maintenance-tickets/{id}/assign")
    ApiEnvelope<MaintenanceService.View> assign(@PathVariable @Positive long id,@Valid @RequestBody Assignment input) {
        return ApiEnvelope.ok(maintenance.assign(id,input.assigneeId()));
    }
    @PostMapping("/api/v1/maintenance-tickets/{id}/start")
    ApiEnvelope<MaintenanceService.View> start(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(maintenance.start(id));
    }
    @PostMapping("/api/v1/maintenance-tickets/{id}/finish")
    ApiEnvelope<MaintenanceService.View> finish(@PathVariable @Positive long id,@Valid @RequestBody Note input) {
        return ApiEnvelope.ok(maintenance.finish(id,input.note()));
    }
    @PostMapping("/api/v1/maintenance-tickets/{id}/accept")
    ApiEnvelope<MaintenanceService.View> accept(@PathVariable @Positive long id,@Valid @RequestBody Note input) {
        return ApiEnvelope.ok(maintenance.acceptance(id,true,input.note()));
    }
    @PostMapping("/api/v1/maintenance-tickets/{id}/reject-acceptance")
    ApiEnvelope<MaintenanceService.View> rejectacceptance(@PathVariable @Positive long id,@Valid @RequestBody Note input) {
        return ApiEnvelope.ok(maintenance.acceptance(id,false,input.note()));
    }
    @PostMapping("/api/v1/maintenance-tickets/{id}/cancel")
    ApiEnvelope<MaintenanceService.View> cancel(@PathVariable @Positive long id) {
        return ApiEnvelope.ok(maintenance.cancel(id));
    }
    @PostMapping("/api/v1/maintenance-tickets/{id}/review")
    ApiEnvelope<MaintenanceMapper.Review> review(@PathVariable @Positive long id,@Valid @RequestBody Rating input) {
        return ApiEnvelope.ok(maintenance.review(id,input));
    }
}
