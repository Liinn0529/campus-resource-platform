package cn.campus.resource.reservation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
record Actor(long id, String username, Set<String> roles, Set<String> permissions) { }
