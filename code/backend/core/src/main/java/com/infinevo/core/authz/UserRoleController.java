package com.infinevo.core.authz;

import java.util.Objects;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code PUT /api/v1/users/{id}/roles} — replaces a user's roles in the bound tenant (W-11.1, spec
 * section 4). {@code id} is the {@code core.user_account} id, which already names one tenant (V009).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserRoleController extends AuthzController {

    private final RoleService roleService;

    public UserRoleController(RoleService roleService) {
        this.roleService = Objects.requireNonNull(roleService, "roleService must not be null");
    }

    /** {@code 200}; {@code 404} when the user or any role is not in this tenant. */
    @PutMapping("/{id}/roles")
    public UserRolesResponse replaceRoles(@PathVariable("id") UUID id, @RequestBody UserRolesRequest request) {
        return roleService.replaceUserRoles(id, request);
    }
}
