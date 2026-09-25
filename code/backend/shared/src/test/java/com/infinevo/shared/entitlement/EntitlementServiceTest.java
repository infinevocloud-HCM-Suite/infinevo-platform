package com.infinevo.shared.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.authz.FakeCacheService;
import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.tenant.TenantContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-12.2 spec section 7 — unit tests for {@link EntitlementService}.
 */
class EntitlementServiceTest {

    private final UUID tenant = UUID.randomUUID();

    private FakeCacheService cache;
    private StubEntitlementSource source;
    private EntitlementService service;

    @BeforeEach
    void setUp() {
        cache = new FakeCacheService();
        source = new StubEntitlementSource();
        source.grant(tenant, PlatformModule.HRMS);
        PermissionCache permissionCache = new PermissionCache(cache);
        service = new EntitlementService(() -> permissionCache, () -> source);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A held module passes")
    void heldModulePasses() {
        TenantContext.set(tenant);

        assertThat(service.holds(PlatformModule.HRMS)).isTrue();
        assertThatCode(() -> service.require(PlatformModule.HRMS)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("An unheld module returns MODULE_NOT_ENTITLED")
    void unheldModuleReturnsModuleNotEntitled() {
        TenantContext.set(tenant);

        assertThat(service.holds(PlatformModule.PAYROLL)).isFalse();
        assertThatThrownBy(() -> service.require(PlatformModule.PAYROLL))
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.MODULE_NOT_ENTITLED));
    }

    @Test
    @DisplayName("A suspended subscription returns TENANT_SUSPENDED on every module endpoint")
    void suspendedSubscriptionReturnsTenantSuspendedOnEveryModule() {
        TenantContext.set(tenant);
        source.grant(tenant, PlatformModule.HRMS, PlatformModule.PAYROLL);
        source.setSuspended(tenant, true);

        assertThat(service.holds(PlatformModule.HRMS)).isFalse();
        assertThat(service.holds(PlatformModule.PAYROLL)).isFalse();

        assertThatThrownBy(() -> service.require(PlatformModule.HRMS))
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.TENANT_SUSPENDED));

        assertThatThrownBy(() -> service.require(PlatformModule.PAYROLL))
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.TENANT_SUSPENDED));
    }

    @Test
    @DisplayName("An unbound tenant fails closed")
    void unboundTenantFailsClosed() {
        TenantContext.clear();

        assertThat(service.holds(PlatformModule.HRMS)).isFalse();
        assertThatThrownBy(() -> service.require(PlatformModule.HRMS))
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.MODULE_NOT_ENTITLED));
    }

    @Test
    @DisplayName("No EntitlementSource bean fails closed")
    void noEntitlementSourceFailsClosed() {
        TenantContext.set(tenant);
        PermissionCache permissionCache = new PermissionCache(cache);
        EntitlementService withoutSource = new EntitlementService(() -> permissionCache, () -> null);

        assertThat(withoutSource.holds(PlatformModule.HRMS)).isFalse();
        assertThatThrownBy(() -> withoutSource.require(PlatformModule.HRMS))
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.MODULE_NOT_ENTITLED));
    }

    @Test
    @DisplayName("No PermissionCache fails closed")
    void noCacheFailsClosed() {
        TenantContext.set(tenant);
        EntitlementService withoutCache = new EntitlementService(() -> null, () -> source);

        assertThat(withoutCache.holds(PlatformModule.HRMS)).isFalse();
        assertThatThrownBy(() -> withoutCache.require(PlatformModule.HRMS))
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.MODULE_NOT_ENTITLED));
    }

    @Test
    @DisplayName("Revoked module in READ_WRITE mode: safe read passes, mutating request is refused")
    void revokedModuleInReadWriteMode_safeReadPasses_mutatingRefused() {
        TenantContext.set(tenant);
        source.revoke(tenant, PlatformModule.HRMS);

        // Safe read (GET/HEAD) passes
        assertThatCode(() -> service.require(PlatformModule.HRMS, RequiresModule.Mode.READ_WRITE, true))
                .doesNotThrowAnyException();

        // Mutating request (POST/PUT/DELETE) is refused with MODULE_NOT_ENTITLED
        assertThatThrownBy(() -> service.require(PlatformModule.HRMS, RequiresModule.Mode.READ_WRITE, false))
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.MODULE_NOT_ENTITLED));
    }

    @Test
    @DisplayName("Revoked module in ACTIVE_ONLY mode: even safe read is refused")
    void revokedModuleInActiveOnlyMode_safeReadRefused() {
        TenantContext.set(tenant);
        source.revoke(tenant, PlatformModule.HRMS);

        assertThatThrownBy(() -> service.require(PlatformModule.HRMS, RequiresModule.Mode.ACTIVE_ONLY, true))
                .isInstanceOf(EntitlementDeniedException.class)
                .satisfies(e ->
                        assertThat(((EntitlementDeniedException) e).error()).isEqualTo(ApiError.MODULE_NOT_ENTITLED));
    }
}
