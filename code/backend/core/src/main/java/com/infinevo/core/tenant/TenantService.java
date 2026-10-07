package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * Service for provisioning new tenants (W-12.1).
 */
public interface TenantService {

    /** Provisions with no inviting user; refused when the request carries {@code admin_email} (D-42). */
    TenantResponse provisionTenant(TenantRequest request);

    /**
     * Provisions a tenant and, when the request carries {@code admin_email}, invites that address as the new
     * tenant's {@code tenant-admin} (D-42).
     *
     * @param actorUserId the platform user provisioning, recorded as the inviter; required with
     *     {@code admin_email}
     */
    TenantResponse provisionTenant(TenantRequest request, UUID actorUserId);
}
