package com.infinevo.core.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.entitlement.EntitlementSnapshot;
import com.infinevo.shared.entitlement.PlatformModule;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EntitlementReadService} (W-85.1 §7).
 */
class EntitlementReadServiceTest {

    private final UUID tenantId = UUID.randomUUID();

    private SubscriptionRepository subscriptionRepository;
    private SubscriptionModuleRepository subscriptionModuleRepository;
    private EntitlementReadService service;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        subscriptionModuleRepository = mock(SubscriptionModuleRepository.class);
        service = new EntitlementReadService(subscriptionRepository, subscriptionModuleRepository);
    }

    @Test
    @DisplayName("ACTIVE subscription loads modules and is not suspended")
    void activeSubscription() {
        Subscription sub = new Subscription(tenantId, SubscriptionStatus.ACTIVE, LocalDate.now());
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(sub));

        SubscriptionModule m1 = new SubscriptionModule(tenantId, sub, PlatformModule.HRMS, LocalDate.now());
        SubscriptionModule m2 = new SubscriptionModule(tenantId, sub, PlatformModule.PAYROLL, LocalDate.now());
        when(subscriptionModuleRepository.findByTenantId(tenantId)).thenReturn(List.of(m1, m2));

        EntitlementSnapshot snapshot = service.snapshotOf(tenantId);
        assertThat(snapshot.suspended()).isFalse();
        assertThat(snapshot.holds(PlatformModule.HRMS)).isTrue();
        assertThat(snapshot.holds(PlatformModule.PAYROLL)).isTrue();
    }

    @Test
    @DisplayName("SUSPENDED subscription sets suspended=true and still loads modules")
    void suspendedSubscriptionLoadsModules() {
        Subscription sub = new Subscription(tenantId, SubscriptionStatus.SUSPENDED, LocalDate.now());
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(sub));

        SubscriptionModule m1 = new SubscriptionModule(tenantId, sub, PlatformModule.HRMS, LocalDate.now());
        when(subscriptionModuleRepository.findByTenantId(tenantId)).thenReturn(List.of(m1));

        EntitlementSnapshot snapshot = service.snapshotOf(tenantId);
        assertThat(snapshot.suspended()).isTrue();
        assertThat(snapshot.holds(PlatformModule.HRMS)).isTrue();
        assertThat(snapshot.holds(PlatformModule.PAYROLL)).isFalse();
    }

    @Test
    @DisplayName("CANCELLED subscription sets suspended=true and still loads modules")
    void cancelledSubscriptionSetsSuspendedTrueAndLoadsModules() {
        Subscription sub = new Subscription(tenantId, SubscriptionStatus.CANCELLED, LocalDate.now());
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(sub));

        SubscriptionModule m1 = new SubscriptionModule(tenantId, sub, PlatformModule.PAYROLL, LocalDate.now());
        SubscriptionModule m2 = new SubscriptionModule(tenantId, sub, PlatformModule.HRMS, LocalDate.now());
        m2.setRevokedOn(LocalDate.now());
        when(subscriptionModuleRepository.findByTenantId(tenantId)).thenReturn(List.of(m1, m2));

        EntitlementSnapshot snapshot = service.snapshotOf(tenantId);
        assertThat(snapshot.suspended()).isTrue();
        assertThat(snapshot.holds(PlatformModule.PAYROLL)).isTrue();
        assertThat(snapshot.isRevoked(PlatformModule.HRMS)).isTrue();
    }

    @Test
    @DisplayName("No subscription row returns empty snapshot (suspended=false, modules empty)")
    void noSubscriptionReturnsEmpty() {
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        EntitlementSnapshot snapshot = service.snapshotOf(tenantId);
        assertThat(snapshot.suspended()).isFalse();
        assertThat(snapshot.activeModules()).isEmpty();
    }
}
