package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.AdminPermissionRequest;
import com.xiangxik.echat.chatbot.api.dto.AdminPermissionResponse;
import com.xiangxik.echat.chatbot.api.dto.AdminRoleRequest;
import com.xiangxik.echat.chatbot.api.dto.AdminRoleResponse;
import com.xiangxik.echat.chatbot.api.dto.AdminUserRequest;
import com.xiangxik.echat.chatbot.api.dto.AdminUserResponse;
import com.xiangxik.echat.chatbot.service.AdminListQuery;
import com.xiangxik.echat.chatbot.service.AdminIdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/identity")
@Tag(name = "Admin Identity", description = "Manage admin users, roles, and permissions")
public class AdminIdentityController {

    private final AdminIdentityService adminIdentityService;

    public AdminIdentityController(AdminIdentityService adminIdentityService) {
        this.adminIdentityService = adminIdentityService;
    }

    @GetMapping("/users")
    @Operation(summary = "List admin users")
    public List<AdminUserResponse> listUsers(@RequestParam Map<String, String> params) {
        return adminIdentityService.listUsers(AdminListQuery.from(params));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an admin user")
    public AdminUserResponse createUser(@Valid @RequestBody AdminUserRequest request) {
        return adminIdentityService.createUser(request);
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "Update an admin user")
    public AdminUserResponse updateUser(@PathVariable Long id, @Valid @RequestBody AdminUserRequest request) {
        return adminIdentityService.updateUser(id, request);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an admin user")
    public void deleteUser(@PathVariable Long id) {
        adminIdentityService.deleteUser(id);
    }

    @GetMapping("/roles")
    @Operation(summary = "List admin roles")
    public List<AdminRoleResponse> listRoles(@RequestParam Map<String, String> params) {
        return adminIdentityService.listRoles(AdminListQuery.from(params));
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an admin role")
    public AdminRoleResponse createRole(@Valid @RequestBody AdminRoleRequest request) {
        return adminIdentityService.createRole(request);
    }

    @PutMapping("/roles/{id}")
    @Operation(summary = "Update an admin role")
    public AdminRoleResponse updateRole(@PathVariable Long id, @Valid @RequestBody AdminRoleRequest request) {
        return adminIdentityService.updateRole(id, request);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an admin role")
    public void deleteRole(@PathVariable Long id) {
        adminIdentityService.deleteRole(id);
    }

    @GetMapping("/permissions")
    @Operation(summary = "List admin permissions")
    public List<AdminPermissionResponse> listPermissions(@RequestParam Map<String, String> params) {
        return adminIdentityService.listPermissions(AdminListQuery.from(params));
    }

    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an admin permission")
    public AdminPermissionResponse createPermission(@Valid @RequestBody AdminPermissionRequest request) {
        return adminIdentityService.createPermission(request);
    }

    @PutMapping("/permissions/{id}")
    @Operation(summary = "Update an admin permission")
    public AdminPermissionResponse updatePermission(@PathVariable Long id,
                                                    @Valid @RequestBody AdminPermissionRequest request) {
        return adminIdentityService.updatePermission(id, request);
    }

    @DeleteMapping("/permissions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an admin permission")
    public void deletePermission(@PathVariable Long id) {
        adminIdentityService.deletePermission(id);
    }
}
