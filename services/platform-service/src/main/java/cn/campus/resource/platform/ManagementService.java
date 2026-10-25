package cn.campus.resource.platform;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ManagementService {
    record UserView(long id, String username, String displayName, boolean enabled,
            List<String> roles, List<Long> roomIds) { }
    record RoleView(long id, String code, String name, List<String> permissionCodes) { }

    private final ManagementMapper mapper;
    private final AccessPolicy access;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    ManagementService(ManagementMapper mapper, AccessPolicy access, PasswordEncoder passwords,
            AuditService audit) {
        this.mapper = mapper;
        this.access = access;
        this.passwords = passwords;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    List<UserView> users(int limit, int offset) {
        access.requireGlobal("user:manage");
        return mapper.users(limit, offset).stream().map(this::view).toList();
    }

    @Transactional
    UserView create(String username, String displayName, String password, Set<String> roleCodes) {
        access.requireGlobal("user:manage");
        PasswordRules.validate(password);
        validateRoles(roleCodes);
        long id = mapper.insertUser(username, displayName, passwords.encode(password));
        roleCodes.stream().sorted().forEach(code -> mapper.insertUserRole(id, code));
        audit.record("USER_CREATED", "USER", Long.toString(id));
        return view(mapper.user(id));
    }

    @Transactional
    UserView update(long id, String displayName, Boolean enabled) {
        access.requireGlobal("user:manage");
        if (displayName == null && enabled == null) {
            throw invalid("At least one profile field is required");
        }
        if (displayName != null && displayName.isBlank()) {
            throw invalid("Display name must not be blank");
        }
        // A shared role row serializes both ways of removing the last enabled administrator.
        mapper.lockSystemAdminRole();
        ManagementMapper.UserRow user = lockedUser(id);
        boolean nextEnabled = enabled == null ? user.enabled() : enabled;
        if (user.enabled() && !nextEnabled) {
            protectLastAdministrator(id);
        }
        mapper.updateProfile(id, displayName == null ? user.displayName() : displayName, nextEnabled);
        audit.record("USER_UPDATED", "USER", Long.toString(id));
        return view(mapper.user(id));
    }

    @Transactional
    UserView resetPassword(long id, String password) {
        access.requireGlobal("user:manage");
        PasswordRules.validate(password);
        lockedUser(id);
        mapper.resetPassword(id, passwords.encode(password));
        audit.record("USER_PASSWORD_RESET", "USER", Long.toString(id));
        return view(mapper.user(id));
    }

    @Transactional
    UserView replaceRoles(long id, Set<String> roleCodes) {
        access.requireGlobal("user:manage");
        validateRoles(roleCodes);
        mapper.lockSystemAdminRole();
        ManagementMapper.UserRow user = lockedUser(id);
        if (user.enabled() && !roleCodes.contains("SYSTEM_ADMIN")) {
            protectLastAdministrator(id);
        }
        mapper.deleteUserRoles(id);
        roleCodes.stream().sorted().forEach(code -> mapper.insertUserRole(id, code));
        if (!roleCodes.contains("ROOM_ADMIN")) {
            mapper.deleteUserRooms(id);
        }
        audit.record("USER_ROLES_REPLACED", "USER", Long.toString(id));
        return view(mapper.user(id));
    }

    @Transactional
    UserView replaceRooms(long id, Set<Long> roomIds) {
        access.requireGlobal("user:manage");
        lockedUser(id);
        if (!mapper.userRoles(id).contains("ROOM_ADMIN")) {
            throw invalid("Room scopes require the ROOM_ADMIN role");
        }
        if (roomIds.stream().anyMatch(roomId -> !mapper.roomExists(roomId))) {
            throw invalid("Unknown room reference");
        }
        mapper.deleteUserRooms(id);
        roomIds.stream().sorted().forEach(roomId -> mapper.insertUserRoom(id, roomId));
        audit.record("USER_ROOM_SCOPES_REPLACED", "USER", Long.toString(id));
        return view(mapper.user(id));
    }

    @Transactional(readOnly = true)
    List<RoleView> roles() {
        access.requireGlobal("role:manage");
        return mapper.roles().stream().map(this::roleView).toList();
    }

    @Transactional(readOnly = true)
    List<ManagementMapper.PermissionRow> permissions() {
        access.requireGlobal("role:manage");
        return mapper.permissions();
    }

    @Transactional
    RoleView replacePermissions(String code, Set<String> permissionCodes) {
        access.requireGlobal("role:manage");
        ManagementMapper.RoleRow role = mapper.lockRole(code);
        if (role == null) {
            throw new ApiFailure(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "Role was not found");
        }
        if ("SYSTEM_ADMIN".equals(code)) {
            throw new ApiFailure(HttpStatus.CONFLICT, "SYSTEM_ADMIN_PERMISSIONS_FIXED",
                    "SYSTEM_ADMIN permissions are fixed");
        }
        Set<String> known = mapper.permissions().stream().map(ManagementMapper.PermissionRow::code)
                .collect(Collectors.toSet());
        if (!known.containsAll(permissionCodes)) {
            throw invalid("Unknown permission reference");
        }
        mapper.deleteRolePermissions(role.id());
        permissionCodes.stream().sorted().forEach(value -> mapper.insertRolePermission(role.id(), value));
        audit.record("ROLE_PERMISSIONS_REPLACED", "ROLE", code);
        return roleView(role);
    }

    private void validateRoles(Set<String> roleCodes) {
        Set<String> known = mapper.roles().stream().map(ManagementMapper.RoleRow::code)
                .collect(Collectors.toSet());
        if (!known.containsAll(roleCodes)) {
            throw invalid("Unknown role reference");
        }
    }

    private ManagementMapper.UserRow lockedUser(long id) {
        ManagementMapper.UserRow user = mapper.lockUser(id);
        if (user == null) {
            throw new ApiFailure(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User was not found");
        }
        return user;
    }

    private void protectLastAdministrator(long id) {
        if (mapper.userRoles(id).contains("SYSTEM_ADMIN") && mapper.enabledSystemAdmins() <= 1) {
            throw new ApiFailure(HttpStatus.CONFLICT, "LAST_SYSTEM_ADMIN",
                    "The last enabled system administrator must be retained");
        }
    }

    private UserView view(ManagementMapper.UserRow user) {
        return new UserView(user.id(), user.username(), user.displayName(), user.enabled(),
                mapper.userRoles(user.id()), mapper.userRooms(user.id()));
    }

    private RoleView roleView(ManagementMapper.RoleRow role) {
        return new RoleView(role.id(), role.code(), role.name(), mapper.rolePermissions(role.id()));
    }

    private ApiFailure invalid(String message) {
        return new ApiFailure(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_REFERENCE_OR_VALUE", message);
    }
}
