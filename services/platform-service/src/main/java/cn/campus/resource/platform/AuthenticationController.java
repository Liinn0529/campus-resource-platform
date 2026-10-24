package cn.campus.resource.platform;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class AuthenticationController {
    private final AuthenticationService authentication;
    private final AccessPolicy access;

    AuthenticationController(AuthenticationService authentication, AccessPolicy access) {
        this.authentication = authentication;
        this.access = access;
    }

    @PostMapping("/auth/login")
    ApiEnvelope<AuthenticationService.LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiEnvelope.ok(authentication.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    ApiEnvelope<Actor> me() { return ApiEnvelope.ok(access.actor()); }

    @PutMapping("/me/password")
    ApiEnvelope<Map<String, Boolean>> password(@Valid @RequestBody PasswordChange request) {
        authentication.changePassword(request.currentPassword(), request.newPassword());
        return ApiEnvelope.ok(Map.of("changed", true));
    }

    record LoginRequest(@NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "LoginRequest[credentials=redacted]"; }
    }

    record PasswordChange(@NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(max = 72) String newPassword) {
        @Override public String toString() { return "PasswordChange[credentials=redacted]"; }
    }
}
