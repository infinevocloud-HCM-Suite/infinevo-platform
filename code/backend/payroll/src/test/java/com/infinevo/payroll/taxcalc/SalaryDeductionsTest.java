package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.taxcalc.engine.SalaryDeductions;
import com.infinevo.payroll.taxcalc.engine.SalaryDeductions.SalaryDeductionsResult;
import com.infinevo.payroll.taxcalc.reader.model.StandardDeductionRule;
import com.infinevo.shared.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SalaryDeductions} (W-33.2 spec ? 7).
 */
class SalaryDeductionsTest {

    private final StandardDeductionRule stdRule =
            new StandardDeductionRule("2025-2026", "OLD", Money.of("50000"), "Standard deduction");

    @Test
    @DisplayName("salary 12L, HRA 1.8L, std ded 50k, PT 2,400 yields 9,67,600")
    void standardCase() {
        SalaryDeductionsResult result = SalaryDeductions.calculate(
                Money.of("1200000"), Money.of("180000"), stdRule, Money.of("2400"), Money.ZERO);

        assertThat(result.standardDeduction()).isEqualTo(Money.of("50000"));
        assertThat(result.professionalTax()).isEqualTo(Money.of("2400"));
        assertThat(result.incomeFromSalary()).isEqualTo(Money.of("967600"));
    }

    @Test
    @DisplayName("standard deduction capped at salary after HRA exemption when income is lower")
    void standardDeductionCappedAtSalaryNet() {
        SalaryDeductionsResult result =
                SalaryDeductions.calculate(Money.of("100000"), Money.of("70000"), stdRule, Money.ZERO, Money.ZERO);

        // Salary after HRA = 30,000 < 50,000 => std ded = 30,000
        assertThat(result.standardDeduction()).isEqualTo(Money.of("30000"));
        assertThat(result.incomeFromSalary()).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("previous employment income is added to net salary")
    void previousEmploymentAdded() {
        SalaryDeductionsResult result = SalaryDeductions.calculate(
                Money.of("500000"), Money.ZERO, stdRule, Money.of("2500"), Money.of("200000"));

        // 5,00,000 - 50,000 - 2,500 + 2,00,000 = 6,47,500
        assertThat(result.incomeFromSalary()).isEqualTo(Money.of("647500"));
    }
}
