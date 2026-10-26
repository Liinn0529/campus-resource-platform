package cn.campus.resource.classroom;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class AccessPolicy {
    Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Actor actor)) {
            throw new ApiFailure(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        return actor;
    }

    void require(String permission) {
        if (!actor().permissions().contains(permission)) {
            throw new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权执行此操作");
        }
    }
}
