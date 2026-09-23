package com.infinevo.core.tenant.service;

import com.infinevo.core.tenant.dto.TenantEntitlementResponse;
import com.infinevo.shared.entitlement.EntitlementChecker;
import java.util.UUID;

public interface EntitlementService extends EntitlementChecker {
    TenantEntitlementResponse getTenantEntitlements(UUID tenantId);
}
