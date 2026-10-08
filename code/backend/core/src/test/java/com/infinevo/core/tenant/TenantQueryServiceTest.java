package com.infinevo.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.subscription.SubscriptionModuleRepository;
import com.infinevo.core.subscription.SubscriptionRepository;
import com.infinevo.core.subscription.SubscriptionServiceImpl;
import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.PlatformTenant;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class TenantQueryServiceTest {

    private static final UUID PLATFORM_TENANT = PlatformTenant.DEFAULT_PLATFORM_TENANT_ID;
    private static final UUID CUSTOMER_TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private JdbcTemplate jdbcTemplate;
    private PlatformTenant platformTenant;
    private TenantQueryService tenantQueryService;

    private SubscriptionRepository subscriptionRepository;
    private SubscriptionModuleRepository subscriptionModuleRepository;
    private PermissionCache permissionCache;
    private SubscriptionServiceImpl subscriptionService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        platformTenant = new PlatformTenant();
        tenantQueryService = new TenantQueryService(jdbcTemplate, platformTenant);

        subscriptionRepository = mock(SubscriptionRepository.class);
        subscriptionModuleRepository = mock(SubscriptionModuleRepository.class);
        permissionCache = mock(PermissionCache.class);
        subscriptionService = new SubscriptionServiceImpl(
                subscriptionRepository, subscriptionModuleRepository, jdbcTemplate, permissionCache, platformTenant);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("bound to a customer tenant, list() throws PermissionDeniedException before any SQL runs")
    void list_whenCustomerTenant_throwsPermissionDeniedExceptionBeforeSql() {
        TenantContext.set(CUSTOMER_TENANT);

        assertThatThrownBy(() -> tenantQueryService.list())
                .isInstanceOf(PermissionDeniedException.class)
                .satisfies(e ->
                        assertThat(((PermissionDeniedException) e).actionCode()).isEqualTo("core.tenant.provision"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("bound to a customer tenant, getOverview() throws PermissionDeniedException before any SQL runs")
    void getOverview_whenCustomerTenant_throwsPermissionDeniedExceptionBeforeSql() {
        TenantContext.set(CUSTOMER_TENANT);

        assertThatThrownBy(() -> tenantQueryService.getOverview(CUSTOMER_TENANT))
                .isInstanceOf(PermissionDeniedException.class)
                .satisfies(e ->
                        assertThat(((PermissionDeniedException) e).actionCode()).isEqualTo("core.tenant.provision"));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("bound to platform tenant, list() returns tenants from core.list_tenants()")
    void list_whenPlatformTenant_executesSqlAndReturnsTenants() {
        TenantContext.set(PLATFORM_TENANT);

        TenantOverview overview = new TenantOverview(
                CUSTOMER_TENANT,
                "Acme",
                "IN",
                "Asia/Kolkata",
                "active",
                List.of("PAYROLL"),
                Instant.now(),
                LocalDate.now().plusMonths(1),
                5L,
                TenantOverview.AdminInvitation.NONE);

        when(jdbcTemplate.query(eq("SELECT * FROM core.list_tenants()"), any(RowMapper.class)))
                .thenReturn(List.of(overview));

        List<TenantOverview> result = tenantQueryService.list();

        assertThat(result).containsExactly(overview);
        verify(jdbcTemplate).query(eq("SELECT * FROM core.list_tenants()"), any(RowMapper.class));
    }

    @Test
    @DisplayName("bound to platform tenant, getOverview() returns tenant from core.get_tenant_overview(?)")
    void getOverview_whenPlatformTenant_executesSqlAndReturnsTenant() {
        TenantContext.set(PLATFORM_TENANT);

        TenantOverview overview = new TenantOverview(
                CUSTOMER_TENANT,
                "Acme",
                "IN",
                "Asia/Kolkata",
                "active",
                List.of("PAYROLL"),
                Instant.now(),
                LocalDate.now().plusMonths(1),
                5L,
                TenantOverview.AdminInvitation.NONE);

        when(jdbcTemplate.query(
                        eq("SELECT * FROM core.get_tenant_overview(?)"), any(RowMapper.class), eq(CUSTOMER_TENANT)))
                .thenReturn(List.of(overview));

        TenantOverview result = tenantQueryService.getOverview(CUSTOMER_TENANT);

        assertThat(result).isEqualTo(overview);
    }

    @Test
    @DisplayName("bound to platform tenant, getOverview() throws TenantNotFoundException when unknown id")
    void getOverview_whenNotFound_throwsTenantNotFoundException() {
        TenantContext.set(PLATFORM_TENANT);

        when(jdbcTemplate.query(
                        eq("SELECT * FROM core.get_tenant_overview(?)"), any(RowMapper.class), eq(CUSTOMER_TENANT)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> tenantQueryService.getOverview(CUSTOMER_TENANT))
                .isInstanceOf(TenantNotFoundException.class)
                .hasMessageContaining(CUSTOMER_TENANT.toString());
    }

    @Test
    @DisplayName("set_subscription_modules on the Infinevo tenant is refused")
    void updateModules_onPlatformTenant_isRefused() {
        assertThatThrownBy(() -> subscriptionService.updateModules(PLATFORM_TENANT, Set.of(PlatformModule.PAYROLL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform tenant");

        verifyNoInteractions(jdbcTemplate);
    }
}
