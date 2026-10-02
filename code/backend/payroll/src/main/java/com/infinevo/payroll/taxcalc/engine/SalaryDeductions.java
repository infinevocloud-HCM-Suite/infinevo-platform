package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.taxcalc.reader.model.StandardDeductionRule;
import com.infinevo.shared.money.Money;
import java.util.Objects;

/**
 * Pure engine for Section 16 salary deductions under the Old Tax Regime (W-33.2 spec § 3 step 3, step 4).
 *
 * <p>Deducts:
 * <ul>
 *   <li>Section 16(ia) standard deduction: {@code min(rule.amount, salary - hraExemption)}</li>
 *   <li>Section 16(iii) professional tax</li>
 * </ul>
 */
public final class SalaryDeductions {

    private SalaryDeductions() {}

    /**
     * Result of Section 16 salary deductions.
     */
    public record SalaryDeductionsResult(Money standardDeduction, Money professionalTax, Money incomeFromSalary) {

        public SalaryDeductionsResult {
            Objects.requireNonNull(standardDeduction, "standardDeduction must not be null");
            Objects.requireNonNull(professionalTax, "professionalTax must not be null");
            Objects.requireNonNull(incomeFromSalary, "incomeFromSalary must not be null");
        }
    }

    /**
     * Computes Section 16 deductions and net income from salary.
     */
    public static SalaryDeductionsResult calculate(
            Money salary,
            Money hraExemption,
            StandardDeductionRule stdRule,
            Money professionalTax,
            Money prevEmploymentIncome) {

        Objects.requireNonNull(salary, "salary must not be null");
        Objects.requireNonNull(hraExemption, "hraExemption must not be null");
        Objects.requireNonNull(stdRule, "stdRule must not be null");
        Money pt = professionalTax != null ? professionalTax : Money.ZERO;
        Money prevInc = prevEmploymentIncome != null ? prevEmploymentIncome : Money.ZERO;

        Money salaryAfterHra = salary.subtract(hraExemption);
        if (salaryAfterHra.isNegative()) {
            salaryAfterHra = Money.ZERO;
        }

        Money standardDeduction = stdRule.amount().compareTo(salaryAfterHra) < 0 ? stdRule.amount() : salaryAfterHra;

        Money netSalary = salaryAfterHra.subtract(standardDeduction).subtract(pt);
        Money incomeFromSalary = netSalary.add(prevInc);

        return new SalaryDeductionsResult(standardDeduction, pt, incomeFromSalary);
    }
}
