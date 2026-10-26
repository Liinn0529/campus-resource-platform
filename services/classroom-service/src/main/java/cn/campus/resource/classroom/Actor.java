package cn.campus.resource.classroom;

import java.util.Set;

record Actor(long id, String username, Set<String> roles, Set<String> permissions, Set<Long> roomIds) {
    Actor {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
        roomIds = Set.copyOf(roomIds);
    }
}
