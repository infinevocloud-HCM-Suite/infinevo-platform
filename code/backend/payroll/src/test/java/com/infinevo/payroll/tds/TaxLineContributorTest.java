package com.infinevo.payroll.tds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayLine;
import com.infinevo.payroll.payrun.PayRunDays;
import com.infinevo.payroll.payrun.PayRunEmployeeContext;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.payroll.taxcalc.TaxRegime;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TaxLineContributor} (W-36.1 §7).
 *
 * <p>Covers the hand calculations in spec §7:
 * <ul>
 *   <li>Annual tax 120,000, ytd 0, period 2026-04 ⇒ 12 months ⇒ 10,000.0000</li>
 *   <li>Period 2026-10, ytd 60,000 ⇒ 6 months ⇒ 10,000.0000</li>
 *   <li>Ytd 70,000 in 2026-10 ⇒ 8,333.3333</li>
 *   <li>Ytd ≥ annual ⇒ no line</li>
 *   <li>Period before effective_from_period ⇒ no line</li>
 *   <li>No active record ⇒ no line</li>
 *   <li>March with remaining 1,234.5 ⇒ one line of 1,234.5000</li>
 * </ul>
 */
class TaxLineContributorTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID PAYRUN_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();

    private EmployeeTdsService tdsService;
    private EmployeePayRunLineRepository lineRepository;
    private TaxLineContributor contributor;

    @BeforeEach
    void setUp() {
        tdsService = mock(EmployeeTdsService.class);
        lineRepository = mock(EmployeePayRunLineRepository.class);
        contributor = new TaxLineContributor(tdsService, lineRepository);
    }

    @Test
    @DisplayName("April 2026, annual tax 120,000, ytd 0: 12 months remaining ⇒ 10,000.0000 deduction")
    void aprilFullYearSpread() {
        YearMonth period = YearMonth.of(2026, 4);
        EmployeeTds record = record("2026-2027", new BigDecimal("120000.0000"), "2026-04");
        when(tdsService.active(EMPLOYEE_ID, "2026-2027")).thenReturn(Optional.of(record));
        when(lineRepository.sumTaxLines(TENANT, EMPLOYEE_ID, "2026-04", "2026-04", PAYRUN_ID))
                .thenReturn(BigDecimal.ZERO);

        PayRunEmployeeContext ctx = context(period);
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).hasSize(1);
        PayLine line = lines.get(0);
        assertThat(line.kind()).isEqualTo(LineKind.DEDUCTION);
        assertThat(line.source()).isEqualTo(LineSource.TAX);
        assertThat(line.componentCode()).isEqualTo("TDS");
        assertThat(line.componentName()).isEqualTo("Tax deducted at source");
        assertThat(line.amount().raw()).isEqualByComparingTo("10000.0000");
        assertThat(line.taxable()).isFalse();
    }

    @Test
    @DisplayName("October 2026, annual tax 120,000, ytd 60,000: 6 months remaining ⇒ 10,000.0000 deduction")
    void octoberRemainingSixMonths() {
        YearMonth period = YearMonth.of(2026, 10);
        EmployeeTds record = record("2026-2027", new BigDecimal("120000.0000"), "2026-04");
        when(tdsService.active(EMPLOYEE_ID, "2026-2027")).thenReturn(Optional.of(record));
        when(lineRepository.sumTaxLines(TENANT, EMPLOYEE_ID, "2026-04", "2026-10", PAYRUN_ID))
                .thenReturn(new BigDecimal("60000.0000"));

        PayRunEmployeeContext ctx = context(period);
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).amount().raw()).isEqualByComparingTo("10000.0000");
    }

    @Test
    @DisplayName("October 2026, annual tax 120,000, ytd 70,000: 50,000 / 6 ⇒ 8,333.3333 deduction")
    void octoberWithDifferentYtd() {
        YearMonth period = YearMonth.of(2026, 10);
        EmployeeTds record = record("2026-2027", new BigDecimal("120000.0000"), "2026-04");
        when(tdsService.active(EMPLOYEE_ID, "2026-2027")).thenReturn(Optional.of(record));
        when(lineRepository.sumTaxLines(TENANT, EMPLOYEE_ID, "2026-04", "2026-10", PAYRUN_ID))
                .thenReturn(new BigDecimal("70000.0000"));

        PayRunEmployeeContext ctx = context(period);
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).amount().raw()).isEqualByComparingTo("8333.3333");
    }

    @Test
    @DisplayName("Ytd >= annual tax: remaining <= 0 produces no tax line")
    void ytdMeetsOrExceedsAnnualTaxProducesNoLine() {
        YearMonth period = YearMonth.of(2026, 11);
        EmployeeTds record = record("2026-2027", new BigDecimal("120000.0000"), "2026-04");
        when(tdsService.active(EMPLOYEE_ID, "2026-2027")).thenReturn(Optional.of(record));
        when(lineRepository.sumTaxLines(TENANT, EMPLOYEE_ID, "2026-04", "2026-11", PAYRUN_ID))
                .thenReturn(new BigDecimal("120000.0000"));

        PayRunEmployeeContext ctx = context(period);
        assertThat(contributor.contribute(ctx)).isEmpty();

        // Also when YTD exceeds annual tax
        when(lineRepository.sumTaxLines(TENANT, EMPLOYEE_ID, "2026-04", "2026-11", PAYRUN_ID))
                .thenReturn(new BigDecimal("125000.0000"));
        assertThat(contributor.contribute(ctx)).isEmpty();
    }

    @Test
    @DisplayName("Period before effective_from_period produces no tax line")
    void periodBeforeEffectiveFromPeriodProducesNoLine() {
        YearMonth period = YearMonth.of(2026, 5);
        EmployeeTds record = record("2026-2027", new BigDecimal("120000.0000"), "2026-06");
        when(tdsService.active(EMPLOYEE_ID, "2026-2027")).thenReturn(Optional.of(record));

        PayRunEmployeeContext ctx = context(period);
        assertThat(contributor.contribute(ctx)).isEmpty();
    }

    @Test
    @DisplayName("No active TDS record produces no tax line")
    void noActiveRecordProducesNoLine() {
        YearMonth period = YearMonth.of(2026, 7);
        when(tdsService.active(EMPLOYEE_ID, "2026-2027")).thenReturn(Optional.empty());

        PayRunEmployeeContext ctx = context(period);
        assertThat(contributor.contribute(ctx)).isEmpty();
    }

    @Test
    @DisplayName("Off-cycle run produces no tax line, even with an active record")
    void offCycleRunProducesNoLine() {
        YearMonth period = YearMonth.of(2026, 4);
        EmployeeTds record = record("2026-2027", new BigDecimal("120000.0000"), "2026-04");
        when(tdsService.active(EMPLOYEE_ID, "2026-2027")).thenReturn(Optional.of(record));

        PayRunEmployeeContext ctx = context(period, PayRunType.OFF_CYCLE);
        assertThat(contributor.contribute(ctx)).isEmpty();
        verifyNoInteractions(lineRepository);
    }

    @Test
    @DisplayName("No active record: the row is noted; with a record, or off-cycle, it is not")
    void noteSaysWhenThereIsNoRecord() {
        YearMonth period = YearMonth.of(2026, 7);
        when(tdsService.active(EMPLOYEE_ID, "2026-2027")).thenReturn(Optional.empty());
        assertThat(contributor.note(context(period))).isEqualTo("No TDS record for 2026-2027");
        assertThat(contributor.note(context(period, PayRunType.OFF_CYCLE))).isNull();

        when(tdsService.active(EMPLOYEE_ID, "2026-2027"))
                .thenReturn(Optional.of(record("2026-2027", new BigDecimal("120000.0000"), "2026-04")));
        assertThat(contributor.note(context(period))).isNull();
    }

    @Test
    @DisplayName("March 2027 with remaining 1,234.50: 1 month remaining ⇒ 1,234.5000 deduction")
    void marchRemainingSingleMonth() {
        YearMonth period = YearMonth.of(2027, 3);
        EmployeeTds record = record("2026-2027", new BigDecimal("100000.0000"), "2026-04");
        when(tdsService.active(EMPLOYEE_ID, "2026-2027")).thenReturn(Optional.of(record));
        when(lineRepository.sumTaxLines(TENANT, EMPLOYEE_ID, "2026-04", "2027-03", PAYRUN_ID))
                .thenReturn(new BigDecimal("98765.5000")); // 100,000 - 98,765.50 = 1,234.50

        PayRunEmployeeContext ctx = context(period);
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).amount().raw()).isEqualByComparingTo("1234.5000");
    }

    private static EmployeeTds record(String fy, BigDecimal annualTax, String effectiveFrom) {
        return new EmployeeTds(
                TENANT,
                EMPLOYEE_ID,
                fy,
                TaxRegime.NEW,
                TdsSource.OFFICER,
                null,
                new BigDecimal("1200000.0000"),
                new BigDecimal("1000000.0000"),
                annualTax,
                effectiveFrom,
                "test note",
                "tester",
                Instant.now());
    }

    private static PayRunEmployeeContext context(YearMonth period) {
        return context(period, PayRunType.REGULAR);
    }

    private static PayRunEmployeeContext context(YearMonth period, PayRunType runType) {
        EmployeeResponse employee = new EmployeeResponse(
                EMPLOYEE_ID,
                TENANT,
                "EMP-01",
                "Asha",
                null,
                "Rao",
                "FEMALE",
                LocalDate.of(2025, 1, 1),
                null,
                null,
                "asha@example.com",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());

        SalaryVersionResponse version = new SalaryVersionResponse(
                UUID.randomUUID(),
                EMPLOYEE_ID,
                LocalDate.of(2025, 4, 1),
                BigDecimal.valueOf(1200000),
                BigDecimal.valueOf(100000),
                false,
                null,
                null,
                BigDecimal.ZERO,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null);
        WorkingDayBasisResponse basis = new WorkingDayBasisResponse(
                BigDecimal.valueOf(30), BigDecimal.valueOf(30), UUID.randomUUID(), LopRounding.HALF_UP_2);

        return new PayRunEmployeeContext(
                TENANT,
                PAYRUN_ID,
                UUID.randomUUID(),
                period,
                period.atDay(1),
                period.atEndOfMonth(),
                employee,
                version,
                basis,
                LopRounding.HALF_UP_2,
                List.of(),
                PayRunDays.zero(),
                Set.of(),
                List.of(),
                runType);
    }
}
