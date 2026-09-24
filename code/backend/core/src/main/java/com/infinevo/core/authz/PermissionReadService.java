package com.infinevo.core.authz;

import java.util.Set;
import java.util.UUID;

/**
 * What a user may do, as stored (W-11.1, spec section 3) — the seam {@code W-11.2} caches and
 * enforces.
 *
 * <p>Deliberately bare: no cache, no {@code @PreAuthorize}, no check. This ticket stores the answer;
 * {@code W-11.2} decides what to do with it (spec section 2, Out of scope; section 9 names "a
 * permission check appears here just to test it" as a risk). The replacement for Payroll's
 * {@code AuthzServiceImpl.canPerform()}
 * ({@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/serviceimpl/auth/AuthzServiceImpl.java:84-89}),
 * which checked by action as the platform will.
 */
public interface PermissionReadService {

    /**
     * Every action code the user holds in the bound tenant, through every role granted to them.
     *
     * <p>Empty for a user with no roles, and for a user of another tenant — row-level security hides
     * their grants, so there is nothing to distinguish "no such user here" from "holds nothing here",
     * and neither may do anything.
     */
    Set<String> actionsOf(UUID userAccountId);
}
