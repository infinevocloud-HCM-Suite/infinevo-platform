package com.infinevo.core.tenant.dto;

import com.infinevo.shared.entitlement.ModuleCode;
import java.util.List;
import java.util.UUID;

public record TenantEntitlementResponse(UUID tenantId, List<ModuleCode> modules, List<NavItem> navigation) {
    public record NavItem(String id, String title, String path, ModuleCode module) {}
}
