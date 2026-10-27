package cn.campus.resource.maintenance;

import java.util.Set;
record Actor(long id, String username, Set<String> roles, Set<String> permissions) { }
