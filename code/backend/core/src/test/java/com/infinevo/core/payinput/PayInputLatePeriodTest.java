package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the late-input rule (W-19 §7, {@code 12-core-contracts.md} §6 decision 3): a write
 * to a locked period is never lost, it is posted to the next open one, and the response says so.
 */
class PayInputLatePeriodTest {

    private static final UUID TENANT = UUID.randomUUID();

    private PayInputRepository payInputs;
    private PayInputPeriodLockRepository locks;
    private PayInputServiceImpl service;

    @BeforeEach
    void setUp() {
        payInputs = mock(PayInputRepository.class);
        locks = mock(PayInputPeriodLockRepository.class);
        service = new PayInputServiceImpl(payInputs, locks);
        TenantContext.set(TENANT);
        when(payInputs.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("record for a locked period posts to the next open period and the response says so")
    void recordForALockedPeriodIsRedirected() {
        YearMonth requested = YearMonth.of(2026, 4);
        YearMonth nextOpen = YearMonth.of(2026, 5);
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(TENANT, requested)).thenReturn(true);
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(TENANT, nextOpen)).thenReturn(false);

        PayInputResponse response = service.record(new PayInputCommand(
                UUID.randomUUID(), requested, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:late-1"));

        assertThat(response.postedPeriod()).isEqualTo(nextOpen);
        verify(payInputs).saveAndFlush(argThatPeriodIs(nextOpen));
    }

    @Test
    @DisplayName("Two consecutive locked periods are both skipped")
    void twoConsecutiveLockedPeriodsAreBothSkipped() {
        YearMonth requested = YearMonth.of(2026, 4);
        YearMonth firstNext = YearMonth.of(2026, 5);
        YearMonth secondNext = YearMonth.of(2026, 6);
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(TENANT, requested)).thenReturn(true);
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(TENANT, firstNext)).thenReturn(true);
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(TENANT, secondNext)).thenReturn(false);

        PayInputResponse response = service.record(new PayInputCommand(
                UUID.randomUUID(), requested, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:late-2"));

        assertThat(response.postedPeriod()).isEqualTo(secondNext);
    }

    @Test
    @DisplayName("An open period is never redirected")
    void anOpenPeriodIsNotRedirected() {
        YearMonth period = YearMonth.of(2026, 4);
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(TENANT, period)).thenReturn(false);

        PayInputResponse response = service.record(new PayInputCommand(
                UUID.randomUUID(), period, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop:open"));

        assertThat(response.postedPeriod()).isEqualTo(period);
    }

    @Test
    @DisplayName("A reversal of a row in a locked period is redirected exactly like any other late input")
    void reversalOfALockedPeriodIsRedirected() {
        YearMonth requested = YearMonth.of(2026, 4);
        YearMonth nextOpen = YearMonth.of(2026, 5);
        UUID originalId = UUID.randomUUID();
        PayInput original = new PayInput(
                TENANT,
                UUID.randomUUID(),
                requested,
                PayInputKind.AD_HOC_DEDUCTION,
                null,
                new BigDecimal("50.00"),
                "payroll",
                "ded:late",
                null,
                "admin");
        when(payInputs.findByIdAndTenantId(eq(originalId), eq(TENANT))).thenReturn(java.util.Optional.of(original));
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(TENANT, requested)).thenReturn(true);
        when(locks.existsByTenantIdAndPeriodAndRunRefIsNull(TENANT, nextOpen)).thenReturn(false);

        PayInputResponse reversal = service.reverse(originalId, "correction after lock");

        assertThat(reversal.postedPeriod()).isEqualTo(nextOpen);
    }

    private static PayInput argThatPeriodIs(YearMonth period) {
        return org.mockito.ArgumentMatchers.argThat(p -> p != null && period.equals(p.getPeriod()));
    }
}
