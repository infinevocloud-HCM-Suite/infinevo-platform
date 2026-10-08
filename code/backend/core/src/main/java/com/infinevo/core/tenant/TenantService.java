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

    /**
     * Sends the tenant's administrator invitation again (W-73.2): a live one through the user-invitation
     * resend, an expired one as a fresh {@code tenant-admin} invitation to the same address. Platform tenant
     * only.
     *
     * @param actorUserId the platform user resending, recorded as the inviter
     * @throws TenantNotFoundException when no such tenant exists
     * @throws AdminInvitationNotWaitingException when the tenant has no pending or expired administrator
     *     invitation
     */
    void resendAdminInvitation(UUID tenantId, UUID actorUserId);
}
