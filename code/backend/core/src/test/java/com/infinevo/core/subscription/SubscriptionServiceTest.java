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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
