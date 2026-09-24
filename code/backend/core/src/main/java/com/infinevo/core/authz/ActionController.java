package com.infinevo.core.authz;

import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/actions} — the action catalogue (W-11.1, spec section 4).
 *
 * <p><strong>Read only, and it is to stay read only.</strong> Actions are code, not data: they are
 * seeded by {@code V020__action.sql} and only a release adds one. There is no POST, PUT or DELETE
 * here and there must never be — Payroll's runtime {@code createAction}
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/action/ActionServiceImpl.java:24-34})
 * is exactly what this replaces.
 */
@RestController
@RequestMapping("/api/v1/actions")
public class ActionController extends AuthzController {

    private final RoleService roleService;

    public ActionController(RoleService roleService) {
        this.roleService = Objects.requireNonNull(roleService, "roleService must not be null");
    }

    @GetMapping
    public List<ActionResponse> list() {
        return roleService.listActions();
    }
}
