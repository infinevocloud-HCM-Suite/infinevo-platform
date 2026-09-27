package com.infinevo.payroll.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.PercentageOf;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SalarySplitCalculatorTest {

    @Test
    @DisplayName("FLAT components pass through value to monthly amount, and annual amount is 12x")
    void flatComponentsPassThrough() {
        BigDecimal annualCtc = new BigDecimal("120000.0000");
        UUID c1 = UUID.randomUUID();

        SalarySplitCalculator.ComponentInput basic = new SalarySplitCalculator.ComponentInput(
                c1,
                "BASIC",
                "Basic Salary",
                CalculationType.FLAT,
                new BigDecimal("10000.0000"),
                null,
                true,
                "FIXED",
                "MONTHLY",
                null);

        SalarySplitCalculator.SplitOutput split =
                SalarySplitCalculator.calculate(annualCtc, List.of(basic), List.of(), List.of());

        assertThat(split.earnings()).hasSize(1);
        SalarySplitCalculator.CalculatedResult res = split.earnings().get(0);
        assertThat(res.monthlyAmount()).isEqualByComparingTo(new BigDecimal("10000.0000"));
        assertThat(res.annualAmount()).isEqualByComparingTo(new BigDecimal("120000.0000"));
    }

    @Test
    @DisplayName("PERCENTAGE of CTC, BASIC, and GROSS resolves at scale 4 with HALF_UP")
    void percentageResolutions() {
        BigDecimal annualCtc = new BigDecimal("600000.0000"); // 50,000/mo
        UUID basicId = UUID.randomUUID();
        UUID hraId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        // Basic = 40% of CTC -> 240,000/yr = 20,000/mo
        SalarySplitCalculator.ComponentInput basic = new SalarySplitCalculator.ComponentInput(
                basicId,
                "BASIC",
                "Basic Salary",
                CalculationType.PERCENTAGE,
                new BigDecimal("40.00"),
                PercentageOf.CTC,
                true,
                "BASIC",
                "MONTHLY",
                null);

        // HRA = 50% of BASIC -> 120,000/yr = 10,000/mo
        SalarySplitCalculator.ComponentInput hra = new SalarySplitCalculator.ComponentInput(
                hraId,
                "HRA",
                "House Rent Allowance",
                CalculationType.PERCENTAGE,
                new BigDecimal("50.00"),
                PercentageOf.BASIC,
                true,
                "ALLOWANCE",
                "MONTHLY",
                null);

        // Special Allowance = FLAT 20,000/mo = 240,000/yr (balances CTC to 600,000)
        SalarySplitCalculator.ComponentInput special = new SalarySplitCalculator.ComponentInput(
                otherId,
                "SPECIAL",
                "Special Allowance",
                CalculationType.FLAT,
                new BigDecimal("20000.0000"),
                null,
                true,
                "ALLOWANCE",
                "MONTHLY",
                null);

        SalarySplitCalculator.SplitOutput split =
                SalarySplitCalculator.calculate(annualCtc, List.of(basic, hra, special), List.of(), List.of());

        assertThat(split.earnings()).hasSize(3);
        assertThat(split.earnings().get(0).annualAmount()).isEqualByComparingTo(new BigDecimal("240000.0000"));
        assertThat(split.earnings().get(0).monthlyAmount()).isEqualByComparingTo(new BigDecimal("20000.0000"));
        assertThat(split.earnings().get(1).annualAmount()).isEqualByComparingTo(new BigDecimal("120000.0000"));
        assertThat(split.earnings().get(1).monthlyAmount()).isEqualByComparingTo(new BigDecimal("10000.0000"));
        assertThat(split.earnings().get(2).annualAmount()).isEqualByComparingTo(new BigDecimal("240000.0000"));
    }

    @Test
    @DisplayName("BASIC percentage_of without a basic earning in the same version is refused")
    void basicPercentageWithoutBasicEarningRefused() {
        BigDecimal annualCtc = new BigDecimal("600000.0000");
        UUID hraId = UUID.randomUUID();

        SalarySplitCalculator.ComponentInput hra = new SalarySplitCalculator.ComponentInput(
                hraId,
                "HRA",
                "House Rent Allowance",
                CalculationType.PERCENTAGE,
                new BigDecimal("50.00"),
                PercentageOf.BASIC,
                true,
                "ALLOWANCE",
                "MONTHLY",
                null);

        assertThatThrownBy(() -> SalarySplitCalculator.calculate(annualCtc, List.of(hra), List.of(), List.of()))
                .isInstanceOf(SalaryValidationException.class)
                .hasMessageContaining("BASIC percentage_of requires an earning with code or type 'BASIC'");
    }

    @Test
    @DisplayName("Sum-equals-CTC mismatch reports the difference in message")
    void sumEqualsCtcMismatchReportsDifference() {
        BigDecimal annualCtc = new BigDecimal("600000.0000");
        UUID basicId = UUID.randomUUID();

        // Basic = 40% of CTC = 240,000/yr (leaving 360,000 unaccounted for)
        SalarySplitCalculator.ComponentInput basic = new SalarySplitCalculator.ComponentInput(
                basicId,
                "BASIC",
                "Basic Salary",
                CalculationType.PERCENTAGE,
                new BigDecimal("40.00"),
                PercentageOf.CTC,
                true,
                "BASIC",
                "MONTHLY",
                null);

        assertThatThrownBy(() -> SalarySplitCalculator.calculate(annualCtc, List.of(basic), List.of(), List.of()))
                .isInstanceOf(SalaryValidationException.class)
                .hasMessageContaining("The annual sum of components included in CTC")
                .hasMessageContaining("Difference: 360000.00");
    }
}
