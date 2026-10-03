package com.infinevo.payroll.tds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.priorpayroll.PriorPayrollTaxQuery;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.tds.exception.EmployeeTdsConflictException;
import com.infinevo.payroll.tds.exception.EmployeeTdsValidationException;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for employee TDS validation rules (W-36.1 §7).
 *
 * <p>Covers input constraints:
 * <ul>
 *   <li>Bad FY labels (non-consecutive, malformed, null/blank)</li>
 *   <li>Annual taxable income exceeding annual gross</li>
 *   <li>Negative annual tax, gross, or taxable income</li>
 *   <li>Scale greater than 2 on monetary inputs</li>
 *   <li>Effective period falling outside the financial year</li>
 *   <li>Tax regime parsing (OLD / NEW only)</li>
 * </ul>
 */
class EmployeeTdsRulesTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-04-15T00:00:00Z"), ZoneOffset.UTC);

    private EmployeeTdsRepository repository;
    private EmployeePayRunLineRepository lineRepository;
    private EmployeeService employeeService;
    private PriorPayrollTaxQuery priorTax;
    private EmployeeTdsServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        repository = mock(EmployeeTdsRepository.class);
        lineRepository = mock(EmployeePayRunLineRepository.class);
        employeeService = mock(EmployeeService.class);
        priorTax = mock(PriorPayrollTaxQuery.class);

        EmployeeResponse emp = new EmployeeResponse(
                EMPLOYEE_ID,
                TENANT_ID,
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
        when(employeeService.get(EMPLOYEE_ID)).thenReturn(emp);

        service = new EmployeeTdsServiceImpl(repository, lineRepository, employeeService, priorTax, FIXED_CLOCK);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-2028", "2026/2027", "2026", "abcd-efgh", "2026-2026", "2027-2026"})
    @DisplayName("Malformed or non-consecutive financial year label is rejected with validation exception")
    void badFinancialYearLabel(String badFy) {
        TdsFigures figures = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("1200000.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("120000.00"),
                "2026-04",
                null,
                null);

        assertThatThrownBy(() -> service.record(EMPLOYEE_ID, badFy, figures))
                .isInstanceOf(EmployeeTdsValidationException.class);
    }

    @Test
    @DisplayName("Annual taxable income exceeding annual gross is rejected")
    void taxableIncomeExceedingGross() {
        TdsFigures figures = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("500000.00"),
                new BigDecimal("600000.00"),
                new BigDecimal("50000.00"),
                "2026-04",
                null,
                null);

        assertThatThrownBy(() -> service.record(EMPLOYEE_ID, "2026-2027", figures))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("cannot exceed annual gross");
    }

    @Test
    @DisplayName("Negative annual tax is rejected")
    void negativeAnnualTax() {
        TdsFigures figures = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("500000.00"),
                new BigDecimal("400000.00"),
                new BigDecimal("-1.00"),
                "2026-04",
                null,
                null);

        assertThatThrownBy(() -> service.record(EMPLOYEE_ID, "2026-2027", figures))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("Annual tax must be non-negative");
    }

    @Test
    @DisplayName("Negative annual gross or taxable income is rejected")
    void negativeGrossOrTaxableIncome() {
        TdsFigures negativeGross = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("-500000.00"),
                new BigDecimal("400000.00"),
                new BigDecimal("10000.00"),
                "2026-04",
                null,
                null);

        assertThatThrownBy(() -> service.record(EMPLOYEE_ID, "2026-2027", negativeGross))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("Annual gross must be non-negative");

        TdsFigures negativeTaxable = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("500000.00"),
                new BigDecimal("-400000.00"),
                new BigDecimal("10000.00"),
                "2026-04",
                null,
                null);

        assertThatThrownBy(() -> service.record(EMPLOYEE_ID, "2026-2027", negativeTaxable))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("Annual taxable income must be non-negative");
    }

    @Test
    @DisplayName("Scale greater than 2 on monetary inputs is rejected")
    void scaleGreaterThanTwo() {
        TdsFigures taxScale3 = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("500000.00"),
                new BigDecimal("400000.00"),
                new BigDecimal("10000.123"),
                "2026-04",
                null,
                null);

        assertThatThrownBy(() -> service.record(EMPLOYEE_ID, "2026-2027", taxScale3))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("Annual tax scale must be at most 2");

        TdsFigures grossScale3 = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("500000.123"),
                new BigDecimal("400000.00"),
                new BigDecimal("10000.00"),
                "2026-04",
                null,
                null);

        assertThatThrownBy(() -> service.record(EMPLOYEE_ID, "2026-2027", grossScale3))
                .isInstanceOf(EmployeeTdsValidationException.class)
                .hasMessageContaining("Annual gross scale must be at most 2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-03", "2027-04", "2025-12", "invalid-month"})
    @DisplayName("Effective from period outside the financial year is rejected")
    void effectiveFromPeriodOutsideFy(String badPeriod) {
        TdsFigures figures = new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("500000.00"),
                new BigDecimal("400000.00"),
                new BigDecimal("10000.00"),
                badPeriod,
                null,
                null);

        assertThatThrownBy(() -> service.record(EMPLOYEE_ID, "2026-2027", figures))
                .isInstanceOf(EmployeeTdsValidationException.class);
    }

    @Test
    @DisplayName("Tax regime must be OLD or NEW")
    void regimeMustBeOldOrNew() {
        assertThatThrownBy(() -> TaxRegime.from("FLAT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaxRegime.from("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaxRegime.from(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("No effective period at 00:30 IST on 1 May (still 30 April in UTC) defaults to 2026-05")
    void defaultPeriodIsTheIndianMonth() {
        Clock utcClock = Clock.fixed(Instant.parse("2026-04-30T19:00:00Z"), ZoneOffset.UTC);
        EmployeeTdsServiceImpl atMidnight =
                new EmployeeTdsServiceImpl(repository, lineRepository, employeeService, priorTax, utcClock);
        when(repository.saveAndFlush(any(EmployeeTds.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeTds saved = atMidnight.record(EMPLOYEE_ID, "2026-2027", figures(null));

        assertThat(saved.getEffectiveFromPeriod()).isEqualTo("2026-05");
    }

    @Test
    @DisplayName("A concurrent first record losing on the unique index is a conflict, not a 500")
    void concurrentFirstRecordIsConflict() {
        when(repository.saveAndFlush(any(EmployeeTds.class)))
                .thenThrow(new DataIntegrityViolationException("uk_employee_tds_tenant_employee_fy_active"));

        assertThatThrownBy(() -> service.record(EMPLOYEE_ID, "2026-2027", figures("2026-04")))
                .isInstanceOf(EmployeeTdsConflictException.class);
    }

    @Test
    @DisplayName("W-38.2: year to date adds the imported TDS to the run tax lines")
    void yearToDateAddsImportedTax() {
        FinancialYear fy = FinancialYear.parse("2026-2027");
        when(lineRepository.sumTaxLines(TENANT_ID, EMPLOYEE_ID, "2026-04", "2027-03", null))
                .thenReturn(new BigDecimal("10000.0000"));
        when(priorTax.total(TENANT_ID, EMPLOYEE_ID, fy)).thenReturn(new BigDecimal("60000.0000"));

        assertThat(service.yearToDate(EMPLOYEE_ID, "2026-2027")).isEqualByComparingTo("70000.0000");
    }

    private static TdsFigures figures(String effectiveFrom) {
        return new TdsFigures(
                TaxRegime.NEW,
                new BigDecimal("1200000.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("120000.00"),
                effectiveFrom,
                null,
                null);
    }
}
