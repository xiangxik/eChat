package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.AdminLoginResult;
import com.xiangxik.echat.chatbot.api.dto.AdminPermissionRequest;
import com.xiangxik.echat.chatbot.api.dto.AdminPermissionResponse;
import com.xiangxik.echat.chatbot.api.dto.AdminRoleRequest;
import com.xiangxik.echat.chatbot.api.dto.AdminRoleResponse;
import com.xiangxik.echat.chatbot.api.dto.AdminUserRequest;
import com.xiangxik.echat.chatbot.api.dto.AdminUserResponse;
import com.xiangxik.echat.chatbot.domain.model.AdminPermission;
import com.xiangxik.echat.chatbot.domain.model.AdminRole;
import com.xiangxik.echat.chatbot.domain.model.AdminUser;
import com.xiangxik.echat.chatbot.domain.model.AdminUserSession;
import com.xiangxik.echat.chatbot.domain.repository.AdminPermissionRepository;
import com.xiangxik.echat.chatbot.domain.repository.AdminRoleRepository;
import com.xiangxik.echat.chatbot.domain.repository.AdminUserRepository;
import com.xiangxik.echat.chatbot.domain.repository.AdminUserSessionRepository;
import com.xiangxik.echat.chatbot.security.AdminPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminIdentityService {

    private static final Duration SESSION_MAX_AGE = Duration.ofHours(12);
    private static final int SESSION_TOKEN_BYTES = 32;

    private final AdminUserRepository userRepository;
    private final AdminRoleRepository roleRepository;
    private final AdminPermissionRepository permissionRepository;
    private final AdminUserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminIdentityService(AdminUserRepository userRepository, AdminRoleRepository roleRepository,
                                AdminPermissionRepository permissionRepository,
                                AdminUserSessionRepository sessionRepository, PasswordEncoder passwordEncoder,
                                TenantService tenantService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return listUsers(AdminListQuery.empty());
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(AdminListQuery query) {
        List<AdminUser> users = tenantService.currentPrincipalIsSuperAdmin()
                ? userRepository.findAll()
                : userRepository.findByTenantId(tenantService.currentTenantId());
        List<AdminUserResponse> responses = users.stream()
                .map(this::toUserResponse)
                .toList();
        return AdminListQuerySupport.apply(responses, query, user -> matchesUserListQuery(user, query), userSorters(), "username");
    }

    private boolean matchesUserListQuery(AdminUserResponse user, AdminListQuery query) {
        return AdminListQuerySupport.containsAny(query.search(), user.username(), user.displayName(), user.tenantId(),
                user.enabled() ? "enabled" : "disabled", user.systemUser() ? "system" : "custom",
                user.roles().stream().map(AdminRoleResponse::code).toList())
                && AdminListQuerySupport.contains(user.username(), query.value("username"))
                && AdminListQuerySupport.contains(user.displayName(), query.value("displayName"))
                && AdminListQuerySupport.contains(user.tenantId(), query.value("tenantId"))
                && AdminListQuerySupport.equalsBoolean(user.enabled(), query.booleanValue("enabled"))
                && AdminListQuerySupport.equalsBoolean(user.systemUser(), query.booleanValue("systemUser"))
                && (query.value("role") == null || user.roles().stream().anyMatch(role -> AdminListQuerySupport.contains(role.code(), query.value("role"))));
    }

    private Map<String, java.util.function.Function<AdminUserResponse, ?>> userSorters() {
        return Map.ofEntries(
                Map.entry("username", AdminUserResponse::username),
                Map.entry("displayName", AdminUserResponse::displayName),
                Map.entry("tenantId", AdminUserResponse::tenantId),
                Map.entry("enabled", AdminUserResponse::enabled),
                Map.entry("systemUser", AdminUserResponse::systemUser),
                Map.entry("createdAt", AdminUserResponse::createdAt),
                Map.entry("updatedAt", AdminUserResponse::updatedAt)
        );
    }

    @Transactional
    public AdminUserResponse createUser(AdminUserRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Admin username already exists: " + username);
        }
        if (!StringUtils.hasText(request.password())) {
            throw new IllegalArgumentException("Password is required when creating an admin user");
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        applyUser(user, request, true);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse updateUser(Long id, AdminUserRequest request) {
        AdminUser user = findUser(id);
        String username = normalizeUsername(request.username());
        if (user.isSystemUser() && !user.getUsername().equals(username)) {
            throw new IllegalArgumentException("System admin user username cannot be changed");
        }
        userRepository.findByUsername(username)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Admin username already exists: " + username);
                });
        user.setUsername(username);
        applyUser(user, request, false);
        return toUserResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id) {
        AdminUser user = findUser(id);
        if (user.isSystemUser()) {
            throw new IllegalArgumentException("System admin user cannot be deleted");
        }
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public List<AdminRoleResponse> listRoles() {
        return listRoles(AdminListQuery.empty());
    }

    @Transactional(readOnly = true)
    public List<AdminRoleResponse> listRoles(AdminListQuery query) {
        List<AdminRoleResponse> responses = roleRepository.findAll().stream()
                .map(this::toRoleResponse)
                .toList();
        return AdminListQuerySupport.apply(responses, query, role -> matchesRoleListQuery(role, query), roleSorters(), "code");
    }

    private boolean matchesRoleListQuery(AdminRoleResponse role, AdminListQuery query) {
        return AdminListQuerySupport.containsAny(query.search(), role.code(), role.name(), role.description(),
                role.systemRole() ? "system" : "custom", role.permissions().stream().map(AdminPermissionResponse::code).toList())
                && AdminListQuerySupport.contains(role.code(), query.value("code"))
                && AdminListQuerySupport.contains(role.name(), query.value("name"))
                && AdminListQuerySupport.contains(role.description(), query.value("description"))
                && AdminListQuerySupport.equalsBoolean(role.systemRole(), query.booleanValue("systemRole"))
                && (query.value("permission") == null || role.permissions().stream().anyMatch(permission -> AdminListQuerySupport.contains(permission.code(), query.value("permission"))));
    }

    private Map<String, java.util.function.Function<AdminRoleResponse, ?>> roleSorters() {
        return Map.of(
                "code", AdminRoleResponse::code,
                "name", AdminRoleResponse::name,
                "description", AdminRoleResponse::description,
                "systemRole", AdminRoleResponse::systemRole,
                "createdAt", AdminRoleResponse::createdAt,
                "updatedAt", AdminRoleResponse::updatedAt
        );
    }

    @Transactional
    public AdminRoleResponse createRole(AdminRoleRequest request) {
        String code = normalizeCode(request.code());
        if (roleRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Admin role code already exists: " + code);
        }
        AdminRole role = new AdminRole();
        role.setCode(code);
        applyRole(role, request);
        return toRoleResponse(roleRepository.save(role));
    }

    @Transactional
    public AdminRoleResponse updateRole(Long id, AdminRoleRequest request) {
        AdminRole role = findRole(id);
        String code = normalizeCode(request.code());
        roleRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Admin role code already exists: " + code);
                });
        role.setCode(code);
        applyRole(role, request);
        return toRoleResponse(roleRepository.save(role));
    }

    @Transactional
    public void deleteRole(Long id) {
        AdminRole role = findRole(id);
        if (role.isSystemRole()) {
            throw new IllegalArgumentException("System roles cannot be deleted");
        }
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionResponse> listPermissions() {
        return listPermissions(AdminListQuery.empty());
    }

    @Transactional(readOnly = true)
    public List<AdminPermissionResponse> listPermissions(AdminListQuery query) {
        List<AdminPermissionResponse> responses = permissionRepository.findAll().stream()
                .map(this::toPermissionResponse)
                .toList();
        return AdminListQuerySupport.apply(responses, query, permission -> matchesPermissionListQuery(permission, query), permissionSorters(), "code");
    }

    private boolean matchesPermissionListQuery(AdminPermissionResponse permission, AdminListQuery query) {
        return AdminListQuerySupport.containsAny(query.search(), permission.code(), permission.name(), permission.description())
                && AdminListQuerySupport.contains(permission.code(), query.value("code"))
                && AdminListQuerySupport.contains(permission.name(), query.value("name"))
                && AdminListQuerySupport.contains(permission.description(), query.value("description"));
    }

    private Map<String, java.util.function.Function<AdminPermissionResponse, ?>> permissionSorters() {
        return Map.of(
                "code", AdminPermissionResponse::code,
                "name", AdminPermissionResponse::name,
                "description", AdminPermissionResponse::description,
                "createdAt", AdminPermissionResponse::createdAt,
                "updatedAt", AdminPermissionResponse::updatedAt
        );
    }

    @Transactional
    public AdminPermissionResponse createPermission(AdminPermissionRequest request) {
        String code = normalizeCode(request.code());
        if (permissionRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Admin permission code already exists: " + code);
        }
        AdminPermission permission = new AdminPermission();
        permission.setCode(code);
        applyPermission(permission, request);
        return toPermissionResponse(permissionRepository.save(permission));
    }

    @Transactional
    public AdminPermissionResponse updatePermission(Long id, AdminPermissionRequest request) {
        AdminPermission permission = findPermission(id);
        String code = normalizeCode(request.code());
        permissionRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Admin permission code already exists: " + code);
                });
        permission.setCode(code);
        applyPermission(permission, request);
        return toPermissionResponse(permissionRepository.save(permission));
    }

    @Transactional
    public void deletePermission(Long id) {
        permissionRepository.delete(findPermission(id));
    }

    @Transactional
    public Optional<AdminLoginResult> authenticate(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Optional.empty();
        }
        Optional<AdminUser> user = userRepository.findByUsername(normalizeUsername(username));
        if (user.isEmpty() || !user.get().isEnabled() || !passwordEncoder.matches(password, user.get().getPasswordHash())) {
            return Optional.empty();
        }
        sessionRepository.deleteByExpiresAtBefore(Instant.now());
        String token = newSessionToken();
        AdminUserSession session = new AdminUserSession();
        session.setUser(user.get());
        session.setTokenHash(hashToken(token));
        session.setExpiresAt(Instant.now().plus(SESSION_MAX_AGE));
        sessionRepository.save(session);
        return Optional.of(new AdminLoginResult(token));
    }

    @Transactional(readOnly = true)
    public Optional<AdminPrincipal> resolveSessionToken(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return sessionRepository.findByTokenHashAndExpiresAtAfter(hashToken(token), Instant.now())
                .filter(session -> session.getUser().isEnabled())
                .map(session -> toPrincipal(session.getUser()));
    }

    @Transactional
    public void logout(String token) {
        if (StringUtils.hasText(token)) {
            sessionRepository.deleteByTokenHash(hashToken(token));
        }
    }

    private void applyUser(AdminUser user, AdminUserRequest request, boolean create) {
        user.setDisplayName(request.displayName().strip());
        String tenantId = tenantService.currentPrincipalIsSuperAdmin()
            ? tenantService.normalizeTenantId(request.tenantId())
            : tenantService.currentTenantId();
        tenantService.ensureTenant(tenantId, tenantId);
        user.setTenantId(tenantId);
        user.setEnabled(user.isSystemUser() || request.enabled() == null || request.enabled());
        if (StringUtils.hasText(request.password())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        } else if (create) {
            throw new IllegalArgumentException("Password is required when creating an admin user");
        }
        Set<AdminRole> roles = resolveRoles(request.roleIds());
        if (user.isSystemUser()) {
            roles.add(roleRepository.findByCode("SUPER_ADMIN")
                    .orElseThrow(() -> new IllegalArgumentException("SUPER_ADMIN role is required")));
        }
        user.setRoles(roles);
    }

    private void applyRole(AdminRole role, AdminRoleRequest request) {
        role.setName(request.name().strip());
        role.setDescription(StringUtils.hasText(request.description()) ? request.description().strip() : null);
        role.setPermissions(resolvePermissions(request.permissionIds()));
    }

    private void applyPermission(AdminPermission permission, AdminPermissionRequest request) {
        permission.setName(request.name().strip());
        permission.setDescription(StringUtils.hasText(request.description()) ? request.description().strip() : null);
    }

    private Set<AdminRole> resolveRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return roleIds.stream()
                .map(this::findRole)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<AdminPermission> resolvePermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return permissionIds.stream()
                .map(this::findPermission)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private AdminUser findUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("AdminUser", id));
    }

    private AdminRole findRole(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("AdminRole", id));
    }

    private AdminPermission findPermission(Long id) {
        return permissionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("AdminPermission", id));
    }

    private AdminUserResponse toUserResponse(AdminUser user) {
        List<AdminRoleResponse> roles = user.getRoles().stream()
                .sorted(Comparator.comparing(AdminRole::getCode))
                .map(this::toRoleResponse)
                .toList();
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getTenantId(),
                user.isEnabled(),
                user.isSystemUser(),
                roles.stream().map(AdminRoleResponse::id).collect(Collectors.toCollection(LinkedHashSet::new)),
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private AdminRoleResponse toRoleResponse(AdminRole role) {
        List<AdminPermissionResponse> permissions = role.getPermissions().stream()
                .sorted(Comparator.comparing(AdminPermission::getCode))
                .map(this::toPermissionResponse)
                .toList();
        return new AdminRoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                permissions.stream().map(AdminPermissionResponse::id).collect(Collectors.toCollection(LinkedHashSet::new)),
                permissions,
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }

    private AdminPermissionResponse toPermissionResponse(AdminPermission permission) {
        return new AdminPermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getDescription(),
                permission.getCreatedAt(),
                permission.getUpdatedAt()
        );
    }

    private AdminPrincipal toPrincipal(AdminUser user) {
        Set<String> roles = user.getRoles().stream()
                .map(AdminRole::getCode)
                .map(this::normalizeCode)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(AdminPermission::getCode)
                .map(this::normalizeCode)
                .collect(Collectors.toUnmodifiableSet());
        return new AdminPrincipal(user.getUsername(), user.getDisplayName(), user.getTenantId(), roles,
                Map.of("permissions", permissions));
    }

    private String normalizeUsername(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }

    private String newSessionToken() {
        byte[] bytes = new byte[SESSION_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
