package com.infinevo.payroll.tds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayLine;
import com.infinevo.payroll.payrun.PayRunDays;
import com.infinevo.payroll.payrun.PayRunEmployeeContext;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.taxcalc.TaxRegime;
import java.math.BigDecimal;
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
 * Hand calculations verified according to spec.
 */
class TaxLineContributorTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID payrunId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    private EmployeeTdsService employeeTdsService;
    private EmployeePayRunLineRepository lineRepository;
    private TaxLineContributor contributor;

    @BeforeEach
    void setUp() {
        employeeTdsService = mock(EmployeeTdsService.class);
        lineRepository = mock(EmployeePayRunLineRepository.class);
        contributor = new TaxLineContributor(employeeTdsService, lineRepository);
    }

    private EmployeeTds createRecord(BigDecimal annualTax, String effectiveFrom) {
        EmployeeTds record = new EmployeeTds();
        record.setId(UUID.randomUUID());
        record.setTenantId(tenantId);
        record.setEmployeeId(employeeId);
        record.setFinancialYear("2026-2027");
        record.setRegime(TaxRegime.NEW);
        record.setSource(TdsSource.OFFICER);
        record.setAnnualGross(new BigDecimal("600000.0000"));
        record.setAnnualTaxableIncome(new BigDecimal("550000.0000"));
        record.setAnnualTax(annualTax.setScale(4));
        record.setEffectiveFromPeriod(effectiveFrom);
        record.setActive(true);
        return record;
    }

    private PayRunEmployeeContext createContext(YearMonth period) {
        EmployeeResponse employee = new EmployeeResponse(
                employeeId,
                tenantId,
                "EMP01",
                "John",
                null,
                "Doe",
                "MALE",
                LocalDate.of(2025, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null);

        return new PayRunEmployeeContext(
                tenantId,
                payrunId,
                UUID.randomUUID(),
                period,
                period.atDay(1),
                period.atEndOfMonth(),
                employee,
                null,
                null,
                null,
                List.of(),
                PayRunDays.zero(),
                Set.of(),
                List.of(),
                PayRunType.OFF_CYCLE,
                null);
    }

    @Test
    @DisplayName("Hand calculation: annual tax 120,000, ytd 0, period 2026-04 => 12 months => 10,000.0000")
    void aprilFullYearSpread() {
        EmployeeTds record = createRecord(new BigDecimal("120000.0000"), "2026-04");
        when(employeeTdsService.activeEntity(eq(tenantId), eq(employeeId), eq("2026-2027")))
                .thenReturn(Optional.of(record));
        when(lineRepository.sumTaxLines(eq(tenantId), eq(employeeId), eq("2026-04"), eq("2026-04"), eq(payrunId)))
                .thenReturn(BigDecimal.ZERO);

        PayRunEmployeeContext ctx = createContext(YearMonth.of(2026, 4));
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).hasSize(1);
        PayLine line = lines.getFirst();
        assertThat(line.kind()).isEqualTo(LineKind.DEDUCTION);
        assertThat(line.source()).isEqualTo(LineSource.TAX);
        assertThat(line.componentCode()).isEqualTo("TDS");
        assertThat(line.componentName()).isEqualTo("Tax deducted at source");
        assertThat(line.amount().raw()).isEqualByComparingTo("10000.0000");
        assertThat(line.taxable()).isFalse();
    }

    @Test
    @DisplayName("Hand calculation: period 2026-10, ytd 60,000 => 6 months => 10,000.0000")
    void octoberRemainingMonthsSpread() {
        EmployeeTds record = createRecord(new BigDecimal("120000.0000"), "2026-04");
        when(employeeTdsService.activeEntity(eq(tenantId), eq(employeeId), eq("2026-2027")))
                .thenReturn(Optional.of(record));
        when(lineRepository.sumTaxLines(eq(tenantId), eq(employeeId), eq("2026-04"), eq("2026-10"), eq(payrunId)))
                .thenReturn(new BigDecimal("60000.0000"));

        PayRunEmployeeContext ctx = createContext(YearMonth.of(2026, 10));
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).hasSize(1);
        PayLine line = lines.getFirst();
        assertThat(line.amount().raw()).isEqualByComparingTo("10000.0000");
    }

    @Test
    @DisplayName("Hand calculation: ytd 70,000 in 2026-10 => 8,333.3333")
    void octoberRemainingUnevenSpread() {
        EmployeeTds record = createRecord(new BigDecimal("120000.0000"), "2026-04");
        when(employeeTdsService.activeEntity(eq(tenantId), eq(employeeId), eq("2026-2027")))
                .thenReturn(Optional.of(record));
        when(lineRepository.sumTaxLines(eq(tenantId), eq(employeeId), eq("2026-04"), eq("2026-10"), eq(payrunId)))
                .thenReturn(new BigDecimal("70000.0000"));

        PayRunEmployeeContext ctx = createContext(YearMonth.of(2026, 10));
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).hasSize(1);
        PayLine line = lines.getFirst();
        assertThat(line.amount().raw()).isEqualByComparingTo("8333.3333");
    }

    @Test
    @DisplayName("Hand calculation: ytd >= annual => no line")
    void ytdGreaterThanOrEqualToAnnualTaxYieldsNoLine() {
        EmployeeTds record = createRecord(new BigDecimal("120000.0000"), "2026-04");
        when(employeeTdsService.activeEntity(eq(tenantId), eq(employeeId), eq("2026-2027")))
                .thenReturn(Optional.of(record));
        when(lineRepository.sumTaxLines(eq(tenantId), eq(employeeId), any(), any(), eq(payrunId)))
                .thenReturn(new BigDecimal("120000.0000"));

        PayRunEmployeeContext ctx = createContext(YearMonth.of(2026, 10));
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).isEmpty();
    }

    @Test
    @DisplayName("Hand calculation: period before effective_from_period => no line")
    void periodBeforeEffectiveFromPeriodYieldsNoLine() {
        EmployeeTds record = createRecord(new BigDecimal("120000.0000"), "2026-06");
        when(employeeTdsService.activeEntity(eq(tenantId), eq(employeeId), eq("2026-2027")))
                .thenReturn(Optional.of(record));

        PayRunEmployeeContext ctx = createContext(YearMonth.of(2026, 5));
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).isEmpty();
    }

    @Test
    @DisplayName("Hand calculation: no active record => no line and a note")
    void noActiveRecordYieldsNoLine() {
        when(employeeTdsService.activeEntity(eq(tenantId), eq(employeeId), eq("2026-2027")))
                .thenReturn(Optional.empty());

        PayRunEmployeeContext ctx = createContext(YearMonth.of(2026, 4));
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).isEmpty();
    }

    @Test
    @DisplayName("Hand calculation: March with remaining 1,234.5 => one line of 1,234.5000")
    void marchSingleMonthRemaining() {
        EmployeeTds record = createRecord(new BigDecimal("100000.0000"), "2026-04");
        when(employeeTdsService.activeEntity(eq(tenantId), eq(employeeId), eq("2026-2027")))
                .thenReturn(Optional.of(record));
        // remaining = 100000 - 98765.5000 = 1234.5000
        when(lineRepository.sumTaxLines(eq(tenantId), eq(employeeId), eq("2026-04"), eq("2027-03"), eq(payrunId)))
                .thenReturn(new BigDecimal("98765.5000"));

        PayRunEmployeeContext ctx = createContext(YearMonth.of(2027, 3));
        List<PayLine> lines = contributor.contribute(ctx);

        assertThat(lines).hasSize(1);
        PayLine line = lines.getFirst();
        assertThat(line.amount().raw()).isEqualByComparingTo("1234.5000");
    }
}
