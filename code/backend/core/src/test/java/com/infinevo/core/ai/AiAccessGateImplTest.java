package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.subscription.Subscription;
import com.infinevo.core.subscription.SubscriptionRepository;
import com.infinevo.core.subscription.SubscriptionStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-114.1 spec, Tests: {@link AiAccessGateImpl} against mocked repositories, with a fixed clock. */
class AiAccessGateImplTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    // 23:30 on 30 November in UTC is already 1 December in India: the month is UTC's.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-30T23:30:00Z"), ZoneOffset.UTC);
    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);

    private SubscriptionRepository subscriptions;
    private AiUsageRepository usage;
    private AiAccessGateImpl gate;

    @BeforeEach
    void setUp() {
        subscriptions = mock(SubscriptionRepository.class);
        usage = mock(AiUsageRepository.class);
        gate = new AiAccessGateImpl(subscriptions, usage, CLOCK);
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("off: consume throws AiDisabledException and counts nothing")
    void consume_whenOff_throwsDisabledAndCountsNothing() {
        Subscription sub = subscription(false, 500);
        when(subscriptions.findByTenantId(TENANT_ID)).thenReturn(Optional.of(sub));

        assertThatThrownBy(gate::consume).isInstanceOf(AiDisabledException.class);

        verify(usage, never()).countOne(any(), any(), anyInt());
    }

    @Test
    @DisplayName("no subscription row is off: status reports enabled = false, consume throws AiDisabledException")
    void noSubscriptionRow_isOff() {
        when(subscriptions.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        when(usage.findByTenantIdAndUsageMonth(TENANT_ID, NOVEMBER)).thenReturn(Optional.empty());

        AiStatus status = gate.status();
        assertThat(status).isEqualTo(new AiStatus(false, null, 0, "2026-11"));

        assertThatThrownBy(gate::consume).isInstanceOf(AiDisabledException.class);
        verify(usage, never()).countOne(any(), any(), anyInt());
    }

    @Test
    @DisplayName("at the cap: countOne returns nothing and consume throws AiCapReachedException")
    void consume_atCap_throwsCapReached() {
        when(subscriptions.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription(true, 3)));
        when(usage.countOne(TENANT_ID, NOVEMBER, 3)).thenReturn(Optional.empty());

        assertThatThrownBy(gate::consume)
                .isInstanceOf(AiCapReachedException.class)
                .satisfies(e -> assertThat(((AiCapReachedException) e).monthlyRequestCap())
                        .isEqualTo(3));
    }

    @Test
    @DisplayName("below the cap: consume counts one for the bound tenant in the UTC month and returns the count")
    void consume_belowCap_countsOne() {
        when(subscriptions.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription(true, 500)));
        when(usage.countOne(TENANT_ID, NOVEMBER, 500)).thenReturn(Optional.of(13));

        assertThat(gate.consume()).isEqualTo(13);

        verify(usage).countOne(TENANT_ID, NOVEMBER, 500);
    }

    @Test
    @DisplayName("status echoes the switch, the cap and this month's count, with the month as YYYY-MM")
    void status_echoesSettingsAndUsage() {
        when(subscriptions.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription(true, 500)));
        AiUsage row = mock(AiUsage.class);
        when(row.getRequestCount()).thenReturn(12);
        when(usage.findByTenantIdAndUsageMonth(TENANT_ID, NOVEMBER)).thenReturn(Optional.of(row));

        assertThat(gate.status()).isEqualTo(new AiStatus(true, 500, 12, "2026-11"));
    }

    @Test
    @DisplayName("a new month reads and counts a new row")
    void newMonth_usesNewRow() {
        AiAccessGateImpl december = new AiAccessGateImpl(
                subscriptions, usage, Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC));
        LocalDate decemberStart = LocalDate.of(2026, 12, 1);
        when(subscriptions.findByTenantId(TENANT_ID)).thenReturn(Optional.of(subscription(true, 500)));
        when(usage.findByTenantIdAndUsageMonth(TENANT_ID, decemberStart)).thenReturn(Optional.empty());
        when(usage.countOne(TENANT_ID, decemberStart, 500)).thenReturn(Optional.of(1));

        assertThat(december.status()).isEqualTo(new AiStatus(true, 500, 0, "2026-12"));
        assertThat(december.consume()).isEqualTo(1);
    }

    @Test
    @DisplayName("no bound tenant: the gate refuses rather than read anything")
    void noBoundTenant_refuses() {
        TenantContext.clear();

        assertThatThrownBy(gate::status).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(gate::consume).isInstanceOf(IllegalStateException.class);
        verify(subscriptions, never()).findByTenantId(any());
    }

    private static Subscription subscription(boolean enabled, Integer cap) {
        Subscription sub = new Subscription(TENANT_ID, SubscriptionStatus.ACTIVE, LocalDate.of(2026, 1, 1));
        sub.setAiEnabled(enabled);
        sub.setAiMonthlyRequestCap(cap);
        return sub;
    }
}
