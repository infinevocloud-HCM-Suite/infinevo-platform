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
    @DisplayName("W-73.2: bound to a customer tenant, summary() throws PermissionDeniedException before any SQL runs")
    void summary_whenCustomerTenant_throwsBeforeSql() {
        TenantContext.set(CUSTOMER_TENANT);

        assertThatThrownBy(() -> tenantQueryService.summary()).isInstanceOf(PermissionDeniedException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("W-73.2: summary() counts customers by status, keeps the last ten, names the waiting ones and"
            + " leaves the platform tenant out")
    void summary_countsCustomersAndLeavesThePlatformOut() {
        TenantContext.set(PLATFORM_TENANT);
        Instant now = Instant.parse("2026-10-08T10:00:00Z");

        List<TenantOverview> rows = new java.util.ArrayList<>();
        rows.add(tenant(PLATFORM_TENANT, "Infinevo", "ACTIVE", now));
        UUID suspended = UUID.randomUUID();
        rows.add(tenant(suspended, "Suspended Co", "SUSPENDED", now.minus(java.time.Duration.ofDays(40))));
        UUID noSubscription = UUID.randomUUID();
        rows.add(tenant(noSubscription, "Bare Co", null, null));
        UUID newest = null;
        for (int i = 0; i < 11; i++) {
            UUID id = UUID.randomUUID();
            rows.add(tenant(id, "Active " + i, "ACTIVE", now.minus(java.time.Duration.ofDays(i))));
            if (i == 0) {
                newest = id;
            }
        }
        when(jdbcTemplate.query(eq("SELECT * FROM core.list_tenants()"), any(RowMapper.class)))
                .thenReturn(rows);
        Instant expiry = now.plus(java.time.Duration.ofDays(3));
        when(jdbcTemplate.query(eq(WaitingAdminInvitation.SELECT_ALL), any(RowMapper.class)))
                .thenReturn(List.of(
                        new WaitingAdminInvitation(
                                suspended, UUID.randomUUID(), "boss@suspended.test", "EXPIRED", expiry),
                        new WaitingAdminInvitation(
                                PLATFORM_TENANT, UUID.randomUUID(), "staff@infinevo.test", "PENDING", expiry)));

        TenantSummaryResponse summary = tenantQueryService.summary(now);

        assertThat(summary.total()).isEqualTo(13);
        assertThat(summary.byStatus())
                .containsEntry("ACTIVE", 11L)
                .containsEntry("SUSPENDED", 1L)
                .containsEntry("PAST_DUE", 0L)
                .containsEntry("CANCELLED", 0L)
                .containsEntry("NONE", 1L);
        assertThat(summary.createdLast30Days())
                .as("eleven in the window; the 40-day-old one is not")
                .isEqualTo(11);
        assertThat(summary.recent()).hasSize(TenantQueryService.RECENT_LIMIT);
        assertThat(summary.recent().get(0).id()).isEqualTo(newest);
        assertThat(summary.recent())
                .extracting(TenantSummaryResponse.RecentTenant::id)
                .doesNotContain(PLATFORM_TENANT, noSubscription);
        assertThat(summary.waitingForAdmin())
                .containsExactly(new TenantSummaryResponse.WaitingTenant(
                        suspended, "Suspended Co", "boss@suspended.test", "EXPIRED", expiry));
    }

    @Test
    @DisplayName("W-73.2: with no customer tenants, summary() is all zeros and empty tables")
    void summary_withNoCustomers_isEmpty() {
        TenantContext.set(PLATFORM_TENANT);
        when(jdbcTemplate.query(eq("SELECT * FROM core.list_tenants()"), any(RowMapper.class)))
                .thenReturn(List.of(tenant(PLATFORM_TENANT, "Infinevo", "ACTIVE", Instant.now())));
        when(jdbcTemplate.query(eq(WaitingAdminInvitation.SELECT_ALL), any(RowMapper.class)))
                .thenReturn(List.of());

        TenantSummaryResponse summary = tenantQueryService.summary();

        assertThat(summary.total()).isZero();
        assertThat(summary.byStatus()).containsOnlyKeys("ACTIVE", "PAST_DUE", "SUSPENDED", "CANCELLED");
        assertThat(summary.byStatus().values()).containsOnly(0L);
        assertThat(summary.recent()).isEmpty();
        assertThat(summary.waitingForAdmin()).isEmpty();
    }

    private static TenantOverview tenant(UUID id, String name, String status, Instant createdAt) {
        return new TenantOverview(
                id,
                name,
                "IN",
                "Asia/Kolkata",
                status,
                List.of(),
                createdAt,
                null,
                0L,
                TenantOverview.AdminInvitation.NONE);
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
