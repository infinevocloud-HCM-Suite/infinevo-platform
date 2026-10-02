package com.infinevo.core.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.authz.PermissionCache;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class SubscriptionServiceTest {

    private SubscriptionRepository subscriptionRepository;
    private SubscriptionModuleRepository subscriptionModuleRepository;
    private JdbcTemplate jdbcTemplate;
    private PermissionCache permissionCache;
    private SubscriptionServiceImpl subscriptionService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        subscriptionModuleRepository = mock(SubscriptionModuleRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        permissionCache = mock(PermissionCache.class);

        subscriptionService = new SubscriptionServiceImpl(
                subscriptionRepository, subscriptionModuleRepository, jdbcTemplate, permissionCache);

        // Bound to the tenant being changed, so updateModules has nothing to rebind and the only
        // ConnectionCallback the tests count is the stored procedure.
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("PlatformModule has exactly two values — HRMS and PAYROLL (core is not a module)")
    void platformModule_hasExactlyTwoValues() {
        assertThat(PlatformModule.values()).containsExactlyInAnyOrder(PlatformModule.HRMS, PlatformModule.PAYROLL);
    }

    @Test
    @DisplayName("every module change calls PermissionCache.bumpVersion with tenant id exactly once")
    void updateModules_whenChanged_bumpsCacheVersionOnce() {
        Subscription sub = new Subscription(TENANT_ID, SubscriptionStatus.ACTIVE, LocalDate.now());
        when(subscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(sub));

        // Currently holds only PAYROLL
        SubscriptionModule mod = new SubscriptionModule(TENANT_ID, sub, PlatformModule.PAYROLL, LocalDate.now());
        when(subscriptionModuleRepository.findByTenantIdAndRevokedOnIsNull(TENANT_ID))
                .thenReturn(List.of(mod));

        // Update to [HRMS, PAYROLL]
        subscriptionService.updateModules(TENANT_ID, Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));

        verify(jdbcTemplate, times(1)).execute(any(ConnectionCallback.class));
        verify(permissionCache, times(1)).bumpVersion(TENANT_ID);
    }

    @Test
    @DisplayName("updateModules eagerly calls SetupChecklistService.assemble when module changes")
    void updateModules_whenChanged_eagerlyAssemblesChecklist() {
        com.infinevo.core.setup.SetupChecklistService checklistService =
                mock(com.infinevo.core.setup.SetupChecklistService.class);
        SubscriptionServiceImpl serviceWithChecklist = new SubscriptionServiceImpl(
                subscriptionRepository, subscriptionModuleRepository, jdbcTemplate, permissionCache, checklistService);

        Subscription sub = new Subscription(TENANT_ID, SubscriptionStatus.ACTIVE, LocalDate.now());
        when(subscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(sub));

        SubscriptionModule mod = new SubscriptionModule(TENANT_ID, sub, PlatformModule.PAYROLL, LocalDate.now());
        when(subscriptionModuleRepository.findByTenantIdAndRevokedOnIsNull(TENANT_ID))
                .thenReturn(List.of(mod));

        serviceWithChecklist.updateModules(TENANT_ID, Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));

        verify(checklistService, times(1)).assemble(TENANT_ID);
    }

    @Test
    @DisplayName("updateModules from another tenant's binding rebinds to the target and restores the caller's")
    void updateModules_crossTenant_rebindsAndRestores() {
        UUID platformTenant = UUID.randomUUID();
        TenantContext.set(platformTenant);
        com.infinevo.core.setup.SetupChecklistService checklistService =
                mock(com.infinevo.core.setup.SetupChecklistService.class);
        SubscriptionServiceImpl serviceWithChecklist = new SubscriptionServiceImpl(
                subscriptionRepository, subscriptionModuleRepository, jdbcTemplate, permissionCache, checklistService);

        Subscription sub = new Subscription(TENANT_ID, SubscriptionStatus.ACTIVE, LocalDate.now());
        UUID[] boundDuringAssemble = new UUID[1];
        when(subscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(sub));
        when(subscriptionModuleRepository.findByTenantIdAndRevokedOnIsNull(TENANT_ID))
                .thenReturn(List.of(new SubscriptionModule(TENANT_ID, sub, PlatformModule.HRMS, LocalDate.now())));
        when(checklistService.assemble(TENANT_ID)).thenAnswer(invocation -> {
            boundDuringAssemble[0] = TenantContext.require();
            return List.of();
        });

        serviceWithChecklist.updateModules(TENANT_ID, Set.of(PlatformModule.HRMS, PlatformModule.PAYROLL));

        // One callback binds the connection to the target, the other is the stored procedure.
        verify(jdbcTemplate, times(2)).execute(any(ConnectionCallback.class));
        assertThat(boundDuringAssemble[0]).isEqualTo(TENANT_ID);
        assertThat(TenantContext.require()).isEqualTo(platformTenant);
    }

    @Test
    @DisplayName("a no-op module change does not bump cache version or execute stored procedure")
    void updateModules_whenSameModules_doesNotBumpCache() {
        Subscription sub = new Subscription(TENANT_ID, SubscriptionStatus.ACTIVE, LocalDate.now());
        when(subscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(sub));

        SubscriptionModule mod = new SubscriptionModule(TENANT_ID, sub, PlatformModule.PAYROLL, LocalDate.now());
        when(subscriptionModuleRepository.findByTenantIdAndRevokedOnIsNull(TENANT_ID))
                .thenReturn(List.of(mod));

        // Same module set [PAYROLL]
        subscriptionService.updateModules(TENANT_ID, Set.of(PlatformModule.PAYROLL));

        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
        verify(permissionCache, never()).bumpVersion(any());
    }

    @Test
    @DisplayName("every status change calls PermissionCache.bumpVersion with tenant id exactly once")
    void updateStatus_whenChanged_bumpsCacheVersionOnce() {
        Subscription sub = new Subscription(TENANT_ID, SubscriptionStatus.ACTIVE, LocalDate.now());
        when(subscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(sub));

        subscriptionService.updateStatus(TENANT_ID, SubscriptionStatus.SUSPENDED);

        verify(jdbcTemplate, times(1)).execute(any(ConnectionCallback.class));
        verify(permissionCache, times(1)).bumpVersion(TENANT_ID);
    }

    @Test
    @DisplayName(
            "updateStatus from another tenant's binding reads the target's subscription, then restores the caller's")
    void updateStatus_crossTenant_rebindsAndRestores() {
        UUID platformTenant = UUID.randomUUID();
        TenantContext.set(platformTenant);
        Subscription sub = new Subscription(TENANT_ID, SubscriptionStatus.ACTIVE, LocalDate.now());
        UUID[] boundWhenRead = new UUID[1];
        when(subscriptionRepository.findByTenantId(TENANT_ID)).thenAnswer(invocation -> {
            boundWhenRead[0] = TenantContext.require();
            return Optional.of(sub);
        });

        subscriptionService.updateStatus(TENANT_ID, SubscriptionStatus.SUSPENDED);

        assertThat(boundWhenRead[0])
                .as("the lookup runs bound to the target, or row-level security would hide the row (a 404)")
                .isEqualTo(TENANT_ID);
        // One callback binds the connection to the target, the other is the stored procedure.
        verify(jdbcTemplate, times(2)).execute(any(ConnectionCallback.class));
        assertThat(TenantContext.require()).isEqualTo(platformTenant);
    }

    @Test
    @DisplayName("a no-op status change does not bump cache version or execute stored procedure")
    void updateStatus_whenSameStatus_doesNotBumpCache() {
        Subscription sub = new Subscription(TENANT_ID, SubscriptionStatus.ACTIVE, LocalDate.now());
        when(subscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(sub));

        subscriptionService.updateStatus(TENANT_ID, SubscriptionStatus.ACTIVE);

        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
        verify(permissionCache, never()).bumpVersion(any());
    }

    @Test
    @DisplayName("getSubscription throws SubscriptionNotFoundException when subscription is missing")
    void getSubscription_whenMissing_throwsException() {
        when(subscriptionRepository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getSubscription(TENANT_ID))
                .isInstanceOf(SubscriptionNotFoundException.class);
    }
}
