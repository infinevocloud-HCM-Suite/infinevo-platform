package com.infinevo.payroll.priorpayroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-38.1 §7: Unit test for {@link PriorPayrollRowValidator}.
 * Covers: one row per reason in §4, each rejected with that reason and its line number;
 * an empty deduction cell reads as 0; 120000.5 passes, 120000.555 is INVALID_AMOUNT.
 */
class PriorPayrollRowValidatorTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMP_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String FY = "2026-2027";
    // Fixed clock: October 15, 2026
    private static final Instant NOW =
            LocalDate.of(2026, 10, 15).atStartOfDay(ZoneOffset.UTC).toInstant();

    private EmployeeRepository employeeRepository;
    private PayRunRepository payRunRepository;
    private PriorPayrollRowValidator validator;
    private Employee validEmployee;

    @BeforeEach
    void setUp() {
        employeeRepository = mock(EmployeeRepository.class);
        payRunRepository = mock(PayRunRepository.class);
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        validator = new PriorPayrollRowValidator(employeeRepository, payRunRepository, null, fixedClock);

        validEmployee = mock(Employee.class);
        when(validEmployee.getId()).thenReturn(EMP_ID);
        when(validEmployee.getEmployeeNumber()).thenReturn("EMP-001");
        when(validEmployee.getDateOfJoining()).thenReturn(LocalDate.of(2025, 1, 1));
        when(employeeRepository.findByTenantIdAndEmployeeNumberAndDeletedFalse(TENANT_ID, "EMP-001"))
                .thenReturn(Optional.of(validEmployee));
        when(employeeRepository.findByTenantIdAndEmployeeNumberAndDeletedFalse(TENANT_ID, "EMP-UNKNOWN"))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("valid row passes validation and parses amounts correctly")
    void validRowPasses() {
        PriorPayrollRow row =
                new PriorPayrollRow(2, "EMP-001", "2026-05", "100000", "1800", "0", "200", "5000", "93000");

        PriorPayrollRowValidator.ValidationResult result = validator.validate(TENANT_ID, FY, List.of(row));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).hasSize(1);
        PriorPayrollRowValidator.ValidatedPriorPayrollRow valid =
                result.validRows().get(0);
        assertThat(valid.grossEarnings()).isEqualByComparingTo("100000");
        assertThat(valid.epfEmployee()).isEqualByComparingTo("1800");
        assertThat(valid.esiEmployee()).isEqualByComparingTo("0");
        assertThat(valid.professionalTax()).isEqualByComparingTo("200");
        assertThat(valid.tds()).isEqualByComparingTo("5000");
        assertThat(valid.netPay()).isEqualByComparingTo("93000");
    }

    @Test
    @DisplayName("empty deduction cells default to 0")
    void emptyDeductionCellsDefaultToZero() {
        PriorPayrollRow row = new PriorPayrollRow(2, "EMP-001", "2026-05", "100000", "", "", "", "", "100000");

        PriorPayrollRowValidator.ValidationResult result = validator.validate(TENANT_ID, FY, List.of(row));

        assertThat(result.errors()).isEmpty();
        assertThat(result.validRows()).hasSize(1);
        PriorPayrollRowValidator.ValidatedPriorPayrollRow valid =
                result.validRows().get(0);
        assertThat(valid.epfEmployee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(valid.esiEmployee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(valid.professionalTax()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(valid.tds()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(valid.netPay()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("decimal amounts: 120000.5 passes, 120000.555 fails with INVALID_AMOUNT")
    void decimalValidation() {
        PriorPayrollRow validDec =
                new PriorPayrollRow(2, "EMP-001", "2026-05", "120000.5", "0", "0", "0", "0", "120000.5");
        PriorPayrollRow invalidDec =
                new PriorPayrollRow(3, "EMP-001", "2026-06", "120000.555", "0", "0", "0", "0", "120000");

        PriorPayrollRowValidator.ValidationResult result =
                validator.validate(TENANT_ID, FY, List.of(validDec, invalidDec));

        assertThat(result.validRows()).hasSize(1);
        assertThat(result.validRows().get(0).grossEarnings()).isEqualByComparingTo("120000.5");
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).lineNumber()).isEqualTo(3);
        assertThat(result.errors().get(0).reason()).isEqualTo("INVALID_AMOUNT");
    }

    @Test
    @DisplayName("INVALID_FORMAT when row has null fields")
    void invalidFormat() {
        PriorPayrollRow invalidRow = new PriorPayrollRow(2, null, "2026-05", "100000", "0", "0", "0", "0", "100000");

        PriorPayrollRowValidator.ValidationResult result = validator.validate(TENANT_ID, FY, List.of(invalidRow));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).lineNumber()).isEqualTo(2);
        assertThat(result.errors().get(0).reason()).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("UNKNOWN_EMPLOYEE when employee is not found in tenant")
    void unknownEmployee() {
        PriorPayrollRow row = new PriorPayrollRow(2, "EMP-UNKNOWN", "2026-05", "100000", "0", "0", "0", "0", "100000");

        PriorPayrollRowValidator.ValidationResult result = validator.validate(TENANT_ID, FY, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).lineNumber()).isEqualTo(2);
        assertThat(result.errors().get(0).reason()).isEqualTo("UNKNOWN_EMPLOYEE");
    }

    @Test
    @DisplayName("INVALID_PERIOD when period is not YYYY-MM")
    void invalidPeriod() {
        PriorPayrollRow row = new PriorPayrollRow(2, "EMP-001", "2026/05", "100000", "0", "0", "0", "0", "100000");

        PriorPayrollRowValidator.ValidationResult result = validator.validate(TENANT_ID, FY, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).lineNumber()).isEqualTo(2);
        assertThat(result.errors().get(0).reason()).isEqualTo("INVALID_PERIOD");
    }

    @Test
    @DisplayName("OUTSIDE_YEAR when period is outside financial year")
    void outsideYear() {
        // FY is 2026-2027 (2026-04 to 2027-03). 2025-05 is outside FY.
        PriorPayrollRow row = new PriorPayrollRow(2, "EMP-001", "2025-05", "100000", "0", "0", "0", "0", "100000");

        PriorPayrollRowValidator.ValidationResult result = validator.validate(TENANT_ID, FY, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).lineNumber()).isEqualTo(2);
        assertThat(result.errors().get(0).reason()).isEqualTo("OUTSIDE_YEAR");
    }

    @Test
    @DisplayName("NOT_PAST when period is current month or future")
    void notPast() {
        // Clock is 2026-10-15. Current month is 2026-10.
        PriorPayrollRow currentMonthRow =
                new PriorPayrollRow(2, "EMP-001", "2026-10", "100000", "0", "0", "0", "0", "100000");
        PriorPayrollRow futureMonthRow =
                new PriorPayrollRow(3, "EMP-001", "2026-11", "100000", "0", "0", "0", "0", "100000");

        PriorPayrollRowValidator.ValidationResult result =
                validator.validate(TENANT_ID, FY, List.of(currentMonthRow, futureMonthRow));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0).reason()).isEqualTo("NOT_PAST");
        assertThat(result.errors().get(1).reason()).isEqualTo("NOT_PAST");
    }

    @Test
    @DisplayName("BEFORE_JOINING when period ends before employee date of joining")
    void beforeJoining() {
        when(validEmployee.getDateOfJoining()).thenReturn(LocalDate.of(2026, 6, 15));
        // Period 2026-05 ends on 2026-05-31, which is before 2026-06-15
        PriorPayrollRow row = new PriorPayrollRow(2, "EMP-001", "2026-05", "100000", "0", "0", "0", "0", "100000");

        PriorPayrollRowValidator.ValidationResult result = validator.validate(TENANT_ID, FY, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).lineNumber()).isEqualTo(2);
        assertThat(result.errors().get(0).reason()).isEqualTo("BEFORE_JOINING");
    }

    @Test
    @DisplayName("REAL_RUN_EXISTS when a REGULAR run not CANCELLED exists for the period")
    void realRunExists() {
        when(payRunRepository.existsByTenantIdAndPeriodAndRunTypeAndStatusNot(
                        TENANT_ID, "2026-05", PayRunType.REGULAR, PayRunStatus.CANCELLED))
                .thenReturn(true);

        PriorPayrollRow row = new PriorPayrollRow(2, "EMP-001", "2026-05", "100000", "0", "0", "0", "0", "100000");

        PriorPayrollRowValidator.ValidationResult result = validator.validate(TENANT_ID, FY, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).lineNumber()).isEqualTo(2);
        assertThat(result.errors().get(0).reason()).isEqualTo("REAL_RUN_EXISTS");
    }

    @Test
    @DisplayName("NET_TOO_HIGH when net_pay exceeds gross_earnings minus statutory deductions")
    void netTooHigh() {
        // Gross 100000, EPF 1800, PT 200 => max net 98000. But net is 99000.
        PriorPayrollRow row = new PriorPayrollRow(2, "EMP-001", "2026-05", "100000", "1800", "0", "200", "0", "99000");

        PriorPayrollRowValidator.ValidationResult result = validator.validate(TENANT_ID, FY, List.of(row));

        assertThat(result.validRows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).lineNumber()).isEqualTo(2);
        assertThat(result.errors().get(0).reason()).isEqualTo("NET_TOO_HIGH");
    }

    @Test
    @DisplayName("DUPLICATE_IN_FILE when the same employee and period appears more than once")
    void duplicateInFile() {
        PriorPayrollRow row1 = new PriorPayrollRow(2, "EMP-001", "2026-05", "100000", "0", "0", "0", "0", "100000");
        PriorPayrollRow row2 = new PriorPayrollRow(3, "EMP-001", "2026-05", "100000", "0", "0", "0", "0", "100000");

        PriorPayrollRowValidator.ValidationResult result = validator.validate(TENANT_ID, FY, List.of(row1, row2));

        assertThat(result.validRows()).hasSize(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).lineNumber()).isEqualTo(3);
        assertThat(result.errors().get(0).reason()).isEqualTo("DUPLICATE_IN_FILE");
    }
}
