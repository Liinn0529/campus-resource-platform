package cn.campus.resource.platform;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;

final class PasswordRules {
    private PasswordRules() { }

    static void validate(String password) {
        int length = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.isBlank() || length < 12 || length > 72) {
            throw new ApiFailure(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "密码须为 12–72 个 UTF-8 字节");
        }
    }
}
