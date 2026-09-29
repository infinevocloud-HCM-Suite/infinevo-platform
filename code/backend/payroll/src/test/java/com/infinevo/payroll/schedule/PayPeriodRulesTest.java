package com.infinevo.payroll.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PayPeriodServiceImpl} pay-day rule derivation (W-28 §7).
 */
class PayPeriodRulesTest {

    private PayScheduleRepository repository;
    private PayPeriodServiceImpl service;
    private final UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        repository = mock(PayScheduleRepository.class);
        service = new PayPeriodServiceImpl(repository);
    }

    // ── LAST_DAY_OF_PERIOD ────────────────────────────────────────────────────

    @Test
    @DisplayName("LAST_DAY_OF_PERIOD: July 2026 pay date is July 31")
    void lastDayOfPeriod_July() {
        givenSchedule(PayDayRule.LAST_DAY_OF_PERIOD, null, (short) 25, LocalDate.of(2026, 1, 1));
        PayPeriodResponse r = service.periodFor(tenantId, YearMonth.of(2026, 7));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(r.payDate()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(r.cutoffDate()).isEqualTo(LocalDate.of(2026, 7, 25));
    }

    @Test
    @DisplayName("LAST_DAY_OF_PERIOD: February 2026 pay date is Feb 28, cutoff clamped to 28")
    void lastDayOfPeriod_February() {
        givenSchedule(PayDayRule.LAST_DAY_OF_PERIOD, null, (short) 31, LocalDate.of(2026, 1, 1));
        PayPeriodResponse r = service.periodFor(tenantId, YearMonth.of(2026, 2));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(r.payDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        // cutoff clamped: Feb has 28 days, inputCutoffDay=31 → clamps to 28
        assertThat(r.cutoffDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    // ── LAST_WORKING_DAY ──────────────────────────────────────────────────────

    @Test
    @DisplayName("LAST_WORKING_DAY Mon–Fri: July 2026, last day is Thursday Jul 31 → July 31")
    void lastWorkingDay_July_endIsThursday() {
        // July 31 2026 = Thursday (workday) → pay date = Jul 31
        givenScheduleWithDays(
                PayDayRule.LAST_WORKING_DAY, null, (short) 25, LocalDate.of(2026, 1, 1), new Short[] {1, 2, 3, 4, 5});
        PayPeriodResponse r = service.periodFor(tenantId, YearMonth.of(2026, 7));
        assertThat(r.payDate()).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("LAST_WORKING_DAY Mon–Fri: August 2026, last day is Monday Aug 31 → Aug 31")
    void lastWorkingDay_August_endIsMonday() {
        givenScheduleWithDays(
                PayDayRule.LAST_WORKING_DAY, null, (short) 25, LocalDate.of(2026, 1, 1), new Short[] {1, 2, 3, 4, 5});
        PayPeriodResponse r = service.periodFor(tenantId, YearMonth.of(2026, 8));
        // Aug 31 2026 = Monday
        assertThat(r.payDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("LAST_WORKING_DAY Mon–Fri: October 2026, last day Sat Oct 31 → Friday Oct 30")
    void lastWorkingDay_October_endIsSaturday_rollsBackToFriday() {
        // Oct 31 2026 = Saturday, Oct 30 = Friday (workday)
        givenScheduleWithDays(
                PayDayRule.LAST_WORKING_DAY, null, (short) 25, LocalDate.of(2026, 1, 1), new Short[] {1, 2, 3, 4, 5});
        PayPeriodResponse r = service.periodFor(tenantId, YearMonth.of(2026, 10));
        assertThat(r.payDate()).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    // ── SPECIFIC_DAY ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("SPECIFIC_DAY: July period, payDayOfMonth=5 → August 5")
    void specificDay_July_paysOnAugust5() {
        givenSchedule(PayDayRule.SPECIFIC_DAY, (short) 5, (short) 25, LocalDate.of(2026, 1, 1));
        PayPeriodResponse r = service.periodFor(tenantId, YearMonth.of(2026, 7));
        assertThat(r.payDate()).isEqualTo(LocalDate.of(2026, 8, 5));
    }

    @Test
    @DisplayName("SPECIFIC_DAY: January period, payDayOfMonth=28 → February 28")
    void specificDay_January_paysOnFebruary28_clamped() {
        // Feb 2026 has 28 days, pay_day_of_month=28 → Feb 28 (no clamping needed)
        givenSchedule(PayDayRule.SPECIFIC_DAY, (short) 28, (short) 25, LocalDate.of(2026, 1, 1));
        PayPeriodResponse r = service.periodFor(tenantId, YearMonth.of(2026, 1));
        assertThat(r.payDate()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    // ── period-before-first-period guard ─────────────────────────────────────

    @Test
    @DisplayName("Period before firstPeriodStart throws IllegalArgumentException (400)")
    void periodBeforeFirstPeriodStart_throws400() {
        givenSchedule(PayDayRule.LAST_DAY_OF_PERIOD, null, (short) 25, LocalDate.of(2026, 6, 1));
        assertThatThrownBy(() -> service.periodFor(tenantId, YearMonth.of(2026, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before first configured period");
    }

    @Test
    @DisplayName("Period equal to firstPeriodStart month is allowed")
    void periodEqualsFirstPeriodStart_ok() {
        givenSchedule(PayDayRule.LAST_DAY_OF_PERIOD, null, (short) 25, LocalDate.of(2026, 6, 1));
        PayPeriodResponse r = service.periodFor(tenantId, YearMonth.of(2026, 6));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    @DisplayName("No pay schedule throws NoPayScheduleException (409)")
    void noSchedule_throws409() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.periodFor(tenantId, YearMonth.of(2026, 7)))
                .isInstanceOf(NoPayScheduleException.class)
                .hasMessageContaining(tenantId.toString());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void givenSchedule(PayDayRule rule, Short payDayOfMonth, short cutoff, LocalDate firstPeriodStart) {
        givenScheduleWithDays(rule, payDayOfMonth, cutoff, firstPeriodStart, new Short[] {1, 2, 3, 4, 5});
    }

    private void givenScheduleWithDays(
            PayDayRule rule, Short payDayOfMonth, short cutoff, LocalDate firstPeriodStart, Short[] days) {
        PaySchedule s = new PaySchedule(tenantId, days, rule, payDayOfMonth, cutoff, firstPeriodStart);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(s));
    }
}
