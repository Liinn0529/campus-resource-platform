package cn.campus.resource.platform;

import java.util.Set;

public record Actor(long id, String username, String displayName,
        Set<String> roles, Set<String> permissions) {
    public Actor {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
}
