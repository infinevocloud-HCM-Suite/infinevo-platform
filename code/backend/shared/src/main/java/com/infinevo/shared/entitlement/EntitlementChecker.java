package com.infinevo.shared.entitlement;

import java.util.UUID;

public interface EntitlementChecker {
    boolean isTenantEntitled(UUID tenantId, ModuleCode moduleCode);
}
