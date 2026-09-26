package com.infinevo.core.tenant;

/**
 * Service for provisioning new tenants (W-12.1).
 */
public interface TenantService {

    TenantResponse provisionTenant(TenantRequest request);
}
