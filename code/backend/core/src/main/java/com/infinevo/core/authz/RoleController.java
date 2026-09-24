package com.infinevo.core.authz;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/roles} — the four role endpoints of spec section 4 (W-11.1).
 *
 * <p>Thin by rule: unpack, delegate, repack. No endpoint names a tenant; {@code TenantContextFilter}
 * bound it from the verified token before the request arrived, and row-level security enforces it
 * again. No {@code @PreAuthorize} either — enforcement belongs to W-11.2 (spec section 2).
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController extends AuthzController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = Objects.requireNonNull(roleService, "roleService must not be null");
    }

    /** {@code 201}. {@code code} is optional and derived from the name when absent — see {@link RoleCreateRequest}. */
    @PostMapping
    public ResponseEntity<RoleResponse> create(@RequestBody RoleCreateRequest request) {
        RoleResponse created = roleService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id()))
                .body(created);
    }

    @GetMapping
    public List<RoleResponse> list() {
        return roleService.list();
    }

    /** {@code 200}; {@code 409} for a system role. */
    @PutMapping("/{id}")
    public RoleResponse update(@PathVariable("id") UUID id, @RequestBody RoleUpdateRequest request) {
        return roleService.update(id, request);
    }

    /** {@code 204}; {@code 409} for a system role or while any user holds the role. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
