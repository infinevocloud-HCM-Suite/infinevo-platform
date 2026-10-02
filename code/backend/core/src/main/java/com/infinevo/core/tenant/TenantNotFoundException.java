package com.infinevo.core.tenant;

import java.util.UUID;

/**
 * Thrown when a requested tenant overview is not found (W-65.1).
 */
public class TenantNotFoundException extends RuntimeException {

    private final UUID tenantId;

    public TenantNotFoundException(UUID tenantId) {
        super("Tenant not found: " + tenantId);
        this.tenantId = tenantId;
    }

    public UUID tenantId() {
        return tenantId;
    }
}
