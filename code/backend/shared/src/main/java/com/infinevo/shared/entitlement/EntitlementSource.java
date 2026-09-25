package com.infinevo.shared.entitlement;

import java.util.Set;
import java.util.UUID;

/**
 * Where downstream entitlement checks load a tenant's active modules from (W-12.1, W-12.2).
 *
 * <p>A port, not an implementation. Declared in {@code shared} so aspects and filters in other
 * modules can check entitlements without depending on {@code core}. Implemented by
 * {@code core}'s {@code EntitlementReadService}.
 */
public interface EntitlementSource {

    /**
     * Every platform module the tenant currently holds an active entitlement to.
     *
     * <p>Empty, never {@code null}, for a tenant with no subscription or a non-active status.
     *
     * @param tenantId the tenant id
     * @return the set of active modules
     */
    Set<PlatformModule> modulesOf(UUID tenantId);
}
