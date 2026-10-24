package cn.campus.resource.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
class ManagementController {
    record CreateUser(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{2,63}") String username,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Size(max = 72) String password,
            @NotNull @Size(max = 7) Set<@NotBlank @Size(max = 64) String> roleCodes) {
        @Override public String toString() { return "CreateUser[credentials=redacted]"; }
    }
    record UpdateUser(@Size(max = 100) String displayName, Boolean enabled) { }
    record ResetPassword(@NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "ResetPassword[credentials=redacted]"; }
    }
    record ReplaceRoles(@NotNull @Size(max = 7) Set<@NotBlank @Size(max = 64) String> roleCodes) { }
    record ReplaceRooms(@NotNull @Size(max = 1000) Set<@NotNull @Positive Long> roomIds) { }
    record ReplacePermissions(
            @NotNull @Size(max = 14) Set<@NotBlank @Size(max = 100) String> permissionCodes) { }

    private final ManagementService service;

    ManagementController(ManagementService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/users")
    ApiEnvelope<?> users(@RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ApiEnvelope.ok(service.users(limit, offset));
    }

    @PostMapping("/api/v1/users")
    @ResponseStatus(HttpStatus.CREATED)
    ApiEnvelope<?> create(@Valid @RequestBody CreateUser body) {
        return ApiEnvelope.ok(service.create(body.username(), body.displayName(), body.password(),
                body.roleCodes()));
    }

    @PatchMapping("/api/v1/users/{id}")
    ApiEnvelope<?> update(@PathVariable @Positive long id, @Valid @RequestBody UpdateUser body) {
        return ApiEnvelope.ok(service.update(id, body.displayName(), body.enabled()));
    }

    @PutMapping("/api/v1/users/{id}/password")
    ApiEnvelope<?> resetPassword(@PathVariable @Positive long id,
            @Valid @RequestBody ResetPassword body) {
        return ApiEnvelope.ok(service.resetPassword(id, body.password()));
    }

    @PutMapping("/api/v1/users/{id}/roles")
    ApiEnvelope<?> replaceRoles(@PathVariable @Positive long id, @Valid @RequestBody ReplaceRoles body) {
        return ApiEnvelope.ok(service.replaceRoles(id, body.roleCodes()));
    }

    @PutMapping("/api/v1/users/{id}/room-scopes")
    ApiEnvelope<?> replaceRooms(@PathVariable @Positive long id, @Valid @RequestBody ReplaceRooms body) {
        return ApiEnvelope.ok(service.replaceRooms(id, body.roomIds()));
    }

    @GetMapping("/api/v1/roles")
    ApiEnvelope<?> roles() {
        return ApiEnvelope.ok(service.roles());
    }

    @GetMapping("/api/v1/permissions")
    ApiEnvelope<?> permissions() {
        return ApiEnvelope.ok(service.permissions());
    }

    @PutMapping("/api/v1/roles/{code}/permissions")
    ApiEnvelope<?> replacePermissions(@PathVariable @Pattern(regexp = "[A-Z_]{1,64}") String code,
            @Valid @RequestBody ReplacePermissions body) {
        return ApiEnvelope.ok(service.replacePermissions(code, body.permissionCodes()));
    }
}
