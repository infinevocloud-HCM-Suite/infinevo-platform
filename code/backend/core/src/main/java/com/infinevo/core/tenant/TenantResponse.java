package com.infinevo.core.tenant;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;
import java.util.UUID;

/**
 * Representation of a created tenant (W-12.1).
 */
public record TenantResponse(
        UUID id,
        UUID tenantId,
        String name,
        String countryCode,
        String timezone,
        short leaveYearStartMonth,
        Set<PlatformModule> modules) {}
