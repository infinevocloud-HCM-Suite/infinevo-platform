package com.infinevo.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.authz.PermissionDeniedException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformTenantTest {

    private static final UUID PLATFORM_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final PlatformTenant platformTenant = new PlatformTenant();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("defaults to the reserved Infinevo tenant ID")
    void defaultTenantId() {
        assertThat(platformTenant.tenantId()).isEqualTo(PLATFORM_TENANT);
    }

    @Test
    @DisplayName("supports custom tenant ID via UUID or string constructor")
    void customTenantId() {
        UUID custom = UUID.randomUUID();
        PlatformTenant customFromUuid = new PlatformTenant(custom);
        PlatformTenant customFromString = new PlatformTenant(custom.toString());

        assertThat(customFromUuid.tenantId()).isEqualTo(custom);
        assertThat(customFromString.tenantId()).isEqualTo(custom);
    }

    @Test
    @DisplayName("isPlatformTenant returns true only for the matching platform tenant ID")
    void isPlatformTenantChecks() {
        assertThat(platformTenant.isPlatformTenant(PLATFORM_TENANT)).isTrue();
        assertThat(platformTenant.isPlatformTenant(CUSTOMER_TENANT)).isFalse();
        assertThat(platformTenant.isPlatformTenant(null)).isFalse();
    }

    @Test
    @DisplayName("requirePlatformTenant succeeds when bound to the platform tenant")
    void requirePlatformTenantSucceedsWhenBound() {
        TenantContext.set(PLATFORM_TENANT);

        platformTenant.requirePlatformTenant();
    }

    @Test
    @DisplayName("requirePlatformTenant throws PermissionDeniedException when bound to a customer tenant")
    void requirePlatformTenantThrowsWhenBoundToCustomer() {
        TenantContext.set(CUSTOMER_TENANT);

        assertThatThrownBy(platformTenant::requirePlatformTenant)
                .isInstanceOf(PermissionDeniedException.class)
                .satisfies(e ->
                        assertThat(((PermissionDeniedException) e).actionCode()).isEqualTo("core.tenant.provision"));
    }

    @Test
    @DisplayName("requirePlatformTenant throws PermissionDeniedException when unbound")
    void requirePlatformTenantThrowsWhenUnbound() {
        TenantContext.clear();

        assertThatThrownBy(platformTenant::requirePlatformTenant)
                .isInstanceOf(PermissionDeniedException.class)
                .satisfies(e ->
                        assertThat(((PermissionDeniedException) e).actionCode()).isEqualTo("core.tenant.provision"));
    }
}
