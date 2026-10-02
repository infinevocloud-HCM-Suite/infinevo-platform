package com.infinevo.shared.tenant;

import com.infinevo.shared.authz.PermissionDeniedException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Identifies the Infinevo platform tenant and guards platform-only operations (W-65.1).
 *
 * <p>Platform staff are members of the Infinevo-owned tenant (fixed id
 * {@code 00000000-0000-0000-0000-000000000001}). They log in like normal users,
 * but cross-tenant read functions and platform management endpoints require the caller
 * to be bound to this specific tenant.
 */
@Component
public class PlatformTenant {

    public static final String DEFAULT_PLATFORM_TENANT_ID_STR = "00000000-0000-0000-0000-000000000001";
    public static final UUID DEFAULT_PLATFORM_TENANT_ID = UUID.fromString(DEFAULT_PLATFORM_TENANT_ID_STR);

    private final UUID tenantId;

    @Autowired
    public PlatformTenant(
            @Value("${infinevo.platform.tenant-id:" + DEFAULT_PLATFORM_TENANT_ID_STR + "}") String tenantId) {
        this(tenantId != null && !tenantId.isBlank() ? UUID.fromString(tenantId.trim()) : DEFAULT_PLATFORM_TENANT_ID);
    }

    public PlatformTenant(UUID tenantId) {
        this.tenantId = tenantId != null ? tenantId : DEFAULT_PLATFORM_TENANT_ID;
    }

    public PlatformTenant() {
        this(DEFAULT_PLATFORM_TENANT_ID);
    }

    /** The configured platform tenant UUID. */
    public UUID tenantId() {
        return tenantId;
    }

    /** Returns true if {@code candidate} matches the platform tenant id. */
    public boolean isPlatformTenant(UUID candidate) {
        return candidate != null && tenantId.equals(candidate);
    }

    /**
     * Asserts that the currently bound tenant in {@link TenantContext} is the platform tenant.
     *
     * @throws PermissionDeniedException if no tenant is bound or if the bound tenant is not the platform tenant
     */
    public void requirePlatformTenant() {
        UUID bound = TenantContext.current().orElse(null);
        if (!isPlatformTenant(bound)) {
            throw new PermissionDeniedException("core.tenant.provision");
        }
    }
}
