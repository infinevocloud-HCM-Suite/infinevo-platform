package com.infinevo.core.tenant;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;
import java.util.UUID;

/**
 * Representation of a created tenant (W-12.1).
 *
 * <p>{@code adminInvitationId} is the {@code tenant-admin} user invitation created with the tenant
 * (D-42), {@code null} when no administrator email was given.
 */
public record TenantResponse(
        UUID id,
        UUID tenantId,
        String name,
        String countryCode,
        String timezone,
        short leaveYearStartMonth,
        Set<PlatformModule> modules,
        UUID adminInvitationId) {}
