package cn.campus.resource.platform;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class AccessPolicy {
    private final IdentityMapper identities;

    AccessPolicy(IdentityMapper identities) { this.identities = identities; }

    Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Actor actor)) {
            throw new ApiFailure(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        return actor;
    }

    void require(String permission) {
        if (!actor().permissions().contains(permission)) { deny(); }
    }

    void requireGlobal(String permission) {
        require(permission);
        if (!actor().roles().contains("SYSTEM_ADMIN")) { deny(); }
    }

    boolean hasRoom(long roomId) {
        Actor user = actor();
        return user.roles().contains("SYSTEM_ADMIN") || (user.roles().contains("ROOM_ADMIN")
                && identities.hasRoomScope(user.id(), roomId));
    }

    void requireRoom(long roomId, String permission) {
        require(permission);
        if (!hasRoom(roomId)) { deny(); }
    }

    private void deny() {
        throw new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权执行此操作");
    }
}
