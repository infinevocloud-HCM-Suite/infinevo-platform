package com.infinevo.shared.entitlement;

import java.util.Objects;
import java.util.Set;

/**
 * Snapshot of a tenant's module entitlements and subscription status (W-12.2).
 */
public record EntitlementSnapshot(
        Set<PlatformModule> activeModules, Set<PlatformModule> revokedModules, boolean suspended) {

    public EntitlementSnapshot {
        activeModules = activeModules == null ? Set.of() : Set.copyOf(activeModules);
        revokedModules = revokedModules == null ? Set.of() : Set.copyOf(revokedModules);
    }

    public static EntitlementSnapshot empty() {
        return new EntitlementSnapshot(Set.of(), Set.of(), false);
    }

    public boolean holds(PlatformModule module) {
        return activeModules.contains(Objects.requireNonNull(module, "module must not be null"));
    }

    public boolean isRevoked(PlatformModule module) {
        return revokedModules.contains(Objects.requireNonNull(module, "module must not be null"));
    }
}
