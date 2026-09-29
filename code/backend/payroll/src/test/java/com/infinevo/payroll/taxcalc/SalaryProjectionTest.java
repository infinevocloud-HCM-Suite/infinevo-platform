package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import com.infinevo.payroll.taxcalc.engine.SalaryProjection;
import com.infinevo.payroll.taxcalc.model.SalaryProjectionResult;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SalaryProjection} annual salary projection engine (W-33.1 spec § 3, § 7).
 */
class SalaryProjectionTest {

    private static final FinancialYear FY_2025_2026 = FinancialYear.parse("2025-2026");
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final UUID BASIC_ID = UUID.randomUUID();
    private static final UUID HRA_ID = UUID.randomUUID();
    private static final UUID NON_TAXABLE_ALLOWANCE_ID = UUID.randomUUID();

    @Test
    @DisplayName("joined 2025-10-15, one version -> exactly 6 months projected (Oct to Mar)")
    void joinedMidYearProjectsRemainingMonths() {
        LocalDate dateOfJoining = LocalDate.of(2025, 10, 15);
        UUID versionId = UUID.randomUUID();

        SalaryVersionResponse version = new SalaryVersionResponse(
                versionId,
                EMPLOYEE_ID,
                LocalDate.of(2025, 10, 15),
                BigDecimal.valueOf(1200000),
                BigDecimal.valueOf(100000),
                false,
                null,
                "Initial",
                BigDecimal.ZERO,
                List.of(
                        new SalaryComponentItemResponse(
                                UUID.randomUUID(),
                                BASIC_ID,
                                "BASIC",
                                "Basic",
                                null,
                                null,
                                null,
                                BigDecimal.valueOf(50000),
                                BigDecimal.valueOf(600000),
                                true,
                                true,
                                "MONTHLY",
                                null),
                        new SalaryComponentItemResponse(
                                UUID.randomUUID(),
                                HRA_ID,
                                "HRA",
                                "HRA",
                                null,
                                null,
                                null,
                                BigDecimal.valueOf(25000),
                                BigDecimal.valueOf(300000),
                                true,
                                true,
                                "MONTHLY",
                                null)),
                List.of(),
                List.of());

        SalaryProjectionResult result = SalaryProjection.annual(
                FY_2025_2026, dateOfJoining, date -> version, componentId -> true); // all taxable

        // 6 months (Oct, Nov, Dec, Jan, Feb, Mar) * (50k + 25k = 75k) = 4,50,000
        assertThat(result.months()).hasSize(6);
        assertThat(result.annualTaxableSalary()).isEqualTo(Money.of("450000"));
    }

    @Test
    @DisplayName("revision from 2026-01-01 -> 3 + 3 months at the two rates")
    void revisionMidYearProjectsTwoRates() {
        LocalDate dateOfJoining = LocalDate.of(2025, 10, 1);
        UUID v1Id = UUID.randomUUID();
        UUID v2Id = UUID.randomUUID();

        SalaryVersionResponse v1 = new SalaryVersionResponse(
                v1Id,
                EMPLOYEE_ID,
                LocalDate.of(2025, 10, 1),
                BigDecimal.valueOf(1200000),
                BigDecimal.valueOf(100000),
                false,
                null,
                "Oct rate",
                BigDecimal.ZERO,
                List.of(new SalaryComponentItemResponse(
                        UUID.randomUUID(),
                        BASIC_ID,
                        "BASIC",
                        "Basic",
                        null,
                        null,
                        null,
                        BigDecimal.valueOf(60000),
                        BigDecimal.valueOf(720000),
                        true,
                        true,
                        "MONTHLY",
                        null)),
                List.of(),
                List.of());

        SalaryVersionResponse v2 = new SalaryVersionResponse(
                v2Id,
                EMPLOYEE_ID,
                LocalDate.of(2026, 1, 1),
                BigDecimal.valueOf(1500000),
                BigDecimal.valueOf(125000),
                false,
                null,
                "Jan revision",
                BigDecimal.valueOf(25),
                List.of(new SalaryComponentItemResponse(
                        UUID.randomUUID(),
                        BASIC_ID,
                        "BASIC",
                        "Basic",
                        null,
                        null,
                        null,
                        BigDecimal.valueOf(80000),
                        BigDecimal.valueOf(960000),
                        true,
                        true,
                        "MONTHLY",
                        null)),
                List.of(),
                List.of());

        SalaryProjectionResult result = SalaryProjection.annual(
                FY_2025_2026,
                dateOfJoining,
                date -> date.isBefore(LocalDate.of(2026, 1, 1)) ? v1 : v2,
                componentId -> true);

        // Oct, Nov, Dec @ 60,000 = 1,80,000
        // Jan, Feb, Mar @ 80,000 = 2,40,000
        // Total = 4,20,000
        assertThat(result.months()).hasSize(6);
        assertThat(result.annualTaxableSalary()).isEqualTo(Money.of("420000"));
    }

    @Test
    @DisplayName("non-taxable earning component is excluded from taxable salary sum")
    void nonTaxableEarningExcluded() {
        LocalDate dateOfJoining = LocalDate.of(2025, 4, 1);

        SalaryVersionResponse version = new SalaryVersionResponse(
                UUID.randomUUID(),
                EMPLOYEE_ID,
                LocalDate.of(2025, 4, 1),
                BigDecimal.valueOf(1200000),
                BigDecimal.valueOf(100000),
                false,
                null,
                "Version",
                BigDecimal.ZERO,
                List.of(
                        new SalaryComponentItemResponse(
                                UUID.randomUUID(),
                                BASIC_ID,
                                "BASIC",
                                "Basic",
                                null,
                                null,
                                null,
                                BigDecimal.valueOf(50000),
                                BigDecimal.valueOf(600000),
                                true,
                                true,
                                "MONTHLY",
                                null),
                        new SalaryComponentItemResponse(
                                UUID.randomUUID(),
                                NON_TAXABLE_ALLOWANCE_ID,
                                "EXEMPT_ALLOW",
                                "Exempt Allowance",
                                null,
                                null,
                                null,
                                BigDecimal.valueOf(10000),
                                BigDecimal.valueOf(120000),
                                true,
                                true,
                                "MONTHLY",
                                null)),
                List.of(),
                List.of());

        SalaryProjectionResult result = SalaryProjection.annual(
                FY_2025_2026,
                dateOfJoining,
                date -> version,
                componentId -> !componentId.equals(NON_TAXABLE_ALLOWANCE_ID)); // only BASIC is taxable

        // 12 months @ 50,000 = 6,00,000 (10,000 exempt allowance excluded)
        assertThat(result.months()).hasSize(12);
        assertThat(result.annualTaxableSalary()).isEqualTo(Money.of("600000"));
    }

    @Test
    @DisplayName("no version in force returns ZERO for month and adds assumption line")
    void noVersionInForceAddsZeroAndAssumption() {
        LocalDate dateOfJoining = LocalDate.of(2025, 4, 1);

        SalaryProjectionResult result = SalaryProjection.annual(
                FY_2025_2026,
                dateOfJoining,
                date -> null, // no version
                componentId -> true);

        assertThat(result.months()).hasSize(12);
        assertThat(result.annualTaxableSalary()).isEqualTo(Money.ZERO);
        assertThat(result.assumptions()).anyMatch(a -> a.contains("No salary structure in force for 2025-04"));
    }
}
