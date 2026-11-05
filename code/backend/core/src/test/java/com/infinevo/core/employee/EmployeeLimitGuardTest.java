package com.infinevo.core.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.subscription.Subscription;
import com.infinevo.core.subscription.SubscriptionRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * W-84.2 — the employee-limit gate. Which rows count (terminated and deleted out, suspended in) is the
 * repository's query, proven against Postgres in {@link EmployeeLimitIT}; here it is pinned that the guard asks
 * for everything but {@code TERMINATED} in the bound tenant.
 */
class EmployeeLimitGuardTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private EmployeeLimitGuard guard;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SubscriptionRepository> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(subscriptions);
        guard = new EmployeeLimitGuard(provider, employees);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    private void limit(Integer limit, long used) {
        Subscription subscription = mock(Subscription.class);
        when(subscription.getEmployeeLimit()).thenReturn(limit);
        when(subscriptions.findByTenantId(TENANT)).thenReturn(Optional.of(subscription));
        when(employees.countByTenantIdAndDeletedFalseAndStatusNot(TENANT, EmploymentStatus.TERMINATED))
                .thenReturn(used);
    }

    @Test
    @DisplayName("a null limit never counts and never throws")
    void nullLimitIsNoGate() {
        limit(null, 1_000);

        assertThatCode(() -> guard.checkRoomFor(500)).doesNotThrowAnyException();
        verifyNoInteractions(employees);
    }

    @Test
    @DisplayName("no subscription row is no gate")
    void noSubscriptionIsNoGate() {
        when(subscriptions.findByTenantId(TENANT)).thenReturn(Optional.empty());

        assertThatCode(() -> guard.checkRoomFor(1)).doesNotThrowAnyException();
        assertThat(guard.usage().room()).isNull();
    }

    @Test
    @DisplayName("used + adding > limit throws, with both numbers in the message")
    void overTheLimitThrows() {
        limit(2, 2);

        assertThatThrownBy(() -> guard.checkRoomFor(1))
                .isInstanceOfSatisfying(EmployeeService.EmployeeLimitReachedException.class, e -> {
                    assertThat(e.limit()).isEqualTo(2);
                    assertThat(e.used()).isEqualTo(2);
                })
                .hasMessage("Your plan allows 2 employees and 2 are in use. Contact Infinevo to raise the limit.");
    }

    @Test
    @DisplayName("used + adding == limit passes")
    void exactlyAtTheLimitPasses() {
        limit(3, 2);

        assertThatCode(() -> guard.checkRoomFor(1)).doesNotThrowAnyException();
        verify(employees).countByTenantIdAndDeletedFalseAndStatusNot(TENANT, EmploymentStatus.TERMINATED);
    }

    @Test
    @DisplayName("room() is null when unlimited, limit - used otherwise, never negative")
    void room() {
        limit(null, 7);
        assertThat(guard.usage().room()).isNull();

        limit(10, 7);
        assertThat(guard.usage().room()).isEqualTo(3);
        assertThat(guard.usage().limit()).isEqualTo(10);
        assertThat(guard.usage().used()).isEqualTo(7);

        limit(5, 9);
        assertThat(guard.usage().room()).isZero();
    }

    @Test
    @DisplayName("no subscription repository in the context: no gate, and nothing counted")
    void noRepositoryIsNoGate() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SubscriptionRepository> none = mock(ObjectProvider.class);
        EmployeeLimitGuard unwired = new EmployeeLimitGuard(none, employees);

        assertThatCode(() -> unwired.checkRoomFor(1)).doesNotThrowAnyException();
        verify(subscriptions, org.mockito.Mockito.never()).findByTenantId(any());
        verifyNoInteractions(employees);
    }

    @Test
    @DisplayName("with no tenant bound the guard refuses rather than reading across tenants")
    void unboundTenantIsRefused() {
        TenantContext.clear();

        assertThatThrownBy(() -> guard.checkRoomFor(1)).isInstanceOf(IllegalStateException.class);
    }
}
