package com.infinevo.payroll.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.dashboard.DashboardQueryRepository.LineSum;
import com.infinevo.payroll.dashboard.DashboardQueryRepository.RunRow;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.SkipReason;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-37 §7 — the grouping, totals and empty-tenant rules, with the queries mocked. */
class PayrollDashboardServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    /** 3 October 2026, India: financial year 2026. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private DashboardQueryRepository queries;
    private EmployeeService employeeService;
    private PayrollDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        queries = mock(DashboardQueryRepository.class);
        employeeService = mock(EmployeeService.class);
        service = new PayrollDashboardServiceImpl(queries, employeeService, CLOCK);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Five EPF codes sum by share, BENEFIT is employer, unknown codes ignored, TDS is every TAX line")
    void statutoryGrouping() {
        PayrollDashboardResponse.Statutory s = PayrollDashboardServiceImpl.statutory(List.of(
                sum("EPF_EMPLOYEE", LineKind.DEDUCTION, LineSource.STATUTORY, "1800.0000"),
                sum("EPF_EMPLOYER", LineKind.BENEFIT, LineSource.STATUTORY, "550.0000"),
                sum("EPS_EMPLOYER", LineKind.BENEFIT, LineSource.STATUTORY, "1250.0000"),
                sum("EDLI", LineKind.BENEFIT, LineSource.STATUTORY, "75.0000"),
                sum("EPF_ADMIN", LineKind.BENEFIT, LineSource.STATUTORY, "75.0000"),
                sum("ESI_EMPLOYEE", LineKind.DEDUCTION, LineSource.STATUTORY, "112.5000"),
                sum("ESI_EMPLOYER", LineKind.BENEFIT, LineSource.STATUTORY, "487.5050"),
                sum("PROFESSIONAL_TAX", LineKind.DEDUCTION, LineSource.STATUTORY, "200.0000"),
                sum("LWF_EMPLOYEE", LineKind.DEDUCTION, LineSource.STATUTORY, "999.0000"),
                sum("TDS", LineKind.DEDUCTION, LineSource.TAX, "10000.0000"),
                sum("TDS_PRIOR", LineKind.DEDUCTION, LineSource.TAX, "500.0000")));

        assertThat(s.epf().employee()).isEqualByComparingTo("1800.00");
        assertThat(s.epf().employer()).isEqualByComparingTo("1950.00");
        assertThat(s.esi().employee()).isEqualByComparingTo("112.50");
        assertThat(s.esi().employer()).isEqualByComparingTo("487.51");
        assertThat(s.professionalTax().employee()).isEqualByComparingTo("200.00");
        assertThat(s.professionalTax().employer()).isNull();
        assertThat(s.tds().employee()).isEqualByComparingTo("10500.00");
        assertThat(s.tds().employer()).isNull();
        assertThat(s.epf().employee().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("A STRUCTURE line that borrows a statutory code is not counted")
    void onlyStatutorySourceCounts() {
        PayrollDashboardResponse.Statutory s = PayrollDashboardServiceImpl.statutory(
                List.of(sum("EPF_EMPLOYER", LineKind.BENEFIT, LineSource.STRUCTURE, "1800.0000")));
        assertThat(s.epf().employer()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("year_totals counts PAID runs only; months show COMPUTED, APPROVED and PAID with their tax")
    void yearTotalsPaidOnly() {
        RunRow april = run("2026-04", PayRunStatus.PAID, "100000.0000", "12000.0000", "88000.0000");
        RunRow may = run("2026-05", PayRunStatus.PAID, "100000.0000", "12000.0000", "88000.0000");
        RunRow june = run("2026-06", PayRunStatus.COMPUTED, "105000.0000", "13000.0000", "92000.0000");
        when(queries.recentRuns(TENANT, 6)).thenReturn(List.of(june, may, april));
        when(queries.skippedByReason(TENANT, june.id())).thenReturn(Map.of(SkipReason.NO_BANK_DETAILS, 1L));
        when(queries.runsInPeriods(eq(TENANT), eq("2026-04"), eq("2027-03"), any()))
                .thenReturn(List.of(april, may, june));
        when(queries.taxByRun(eq(TENANT), any()))
                .thenReturn(Map.of(
                        april.id(), new BigDecimal("10000.0000"),
                        may.id(), new BigDecimal("10000.0000"),
                        june.id(), new BigDecimal("11000.0000")));
        when(queries.paidLineSums(eq(TENANT), anyString(), anyString(), any())).thenReturn(List.of());
        when(employeeService.listEmployedBetween(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 3)))
                .thenReturn(List.of());

        PayrollDashboardResponse r = service.summary(null);

        assertThat(r.financialYear().start()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(r.yearTotals().paidRuns()).isEqualTo(2);
        assertThat(r.yearTotals().gross()).isEqualByComparingTo("200000.00");
        assertThat(r.yearTotals().deductions()).isEqualByComparingTo("24000.00");
        assertThat(r.yearTotals().tax()).isEqualByComparingTo("20000.00");
        assertThat(r.yearTotals().netPay()).isEqualByComparingTo("176000.00");
        assertThat(r.months())
                .extracting(PayrollDashboardResponse.MonthRow::period)
                .containsExactly("2026-04", "2026-05", "2026-06");
        assertThat(r.months().get(2).tax()).isEqualByComparingTo("11000.00");
        assertThat(r.currentRun().payrunId()).isEqualTo(june.id());
        assertThat(r.currentRun().progressDone()).isNull();
        assertThat(r.employees().asAtRun().skippedByReason())
                .containsEntry("NO_BANK_DETAILS", 1L)
                .containsEntry("NO_SALARY", 0L);
    }

    @Test
    @DisplayName("No runs: zeros and empty lists, not null; only current_run and as_at_run are null")
    void noRuns() {
        when(queries.recentRuns(any(), anyInt())).thenReturn(List.of());
        when(queries.runsInPeriods(any(), anyString(), anyString(), any())).thenReturn(List.of());
        when(queries.taxByRun(any(), any())).thenReturn(Map.of());
        when(queries.paidLineSums(any(), anyString(), anyString(), any())).thenReturn(List.of());
        when(employeeService.listEmployedBetween(any(), any())).thenReturn(List.of());

        PayrollDashboardResponse r = service.summary(2026);

        assertThat(r.currentRun()).isNull();
        assertThat(r.employees().asAtRun()).isNull();
        assertThat(r.employees().activeToday()).isZero();
        assertThat(r.recentRuns()).isNotNull().isEmpty();
        assertThat(r.months()).isNotNull().isEmpty();
        assertThat(r.yearTotals().gross()).isEqualByComparingTo("0.00");
        assertThat(r.yearTotals().paidRuns()).isZero();
        assertThat(r.statutory().epf().employee()).isEqualByComparingTo("0.00");
        assertThat(r.statutory().epf().employer()).isEqualByComparingTo("0.00");
        assertThat(r.statutory().tds().employee()).isEqualByComparingTo("0.00");
        verify(queries, never()).skippedByReason(any(), any());
    }

    @Test
    @DisplayName("Progress is shown only while COMPUTING")
    void progressOnlyWhileComputing() {
        RunRow computing = new RunRow(
                UUID.randomUUID(),
                "2026-09",
                PayRunStatus.COMPUTING,
                LocalDate.of(2026, 9, 30),
                null,
                10,
                0,
                4,
                10,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        PayrollDashboardResponse.RunCard card = PayrollDashboardServiceImpl.card(computing);
        assertThat(card.progressDone()).isEqualTo(4);
        assertThat(card.progressTotal()).isEqualTo(10);
    }

    @Test
    @DisplayName("fy=1999 and fy=2101 are rejected; 2000 and 2100 are accepted; null is the year containing today")
    void yearRange() {
        LocalDate today = LocalDate.of(2026, 3, 31);
        assertThatThrownBy(() -> PayrollDashboardServiceImpl.resolveYear(1999, today))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PayrollDashboardServiceImpl.resolveYear(2101, today))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PayrollDashboardServiceImpl.resolveYear(2000, today)).isEqualTo(FinancialYear.of(2000));
        assertThat(PayrollDashboardServiceImpl.resolveYear(2100, today)).isEqualTo(FinancialYear.of(2100));
        assertThat(PayrollDashboardServiceImpl.resolveYear(null, today)).isEqualTo(FinancialYear.of(2025));
    }

    private static LineSum sum(String code, LineKind kind, LineSource source, String amount) {
        return new LineSum(code, kind, source, new BigDecimal(amount));
    }

    private static RunRow run(String period, PayRunStatus status, String gross, String deductions, String net) {
        return new RunRow(
                UUID.randomUUID(),
                period,
                status,
                LocalDate.parse(period + "-28"),
                status == PayRunStatus.PAID ? LocalDate.parse(period + "-28") : null,
                3,
                1,
                0,
                0,
                new BigDecimal(gross),
                new BigDecimal(deductions),
                new BigDecimal(net));
    }
}
