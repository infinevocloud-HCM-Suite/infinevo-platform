package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.taxcalc.AgeCategory;
import com.infinevo.payroll.taxcalc.reader.model.OtherIncomeRule;
import com.infinevo.payroll.taxdeclaration.summary.EmployeeInvOtherIncome;
import com.infinevo.payroll.taxdeclaration.summary.OtherIncomeKind;
import com.infinevo.shared.money.Money;
import java.util.List;
import java.util.Objects;

/**
 * Pure engine for Other Income and Section 80TTA / 80TTB interest deductions (W-33.2 spec ? 3 step 6, step 8).
 *
 * <p>Section 80TTA applies to {@link AgeCategory#GENERAL} on savings account interest.
 * Section 80TTB applies to senior and super-senior citizens on total savings plus deposit interest.
 */
public final class OtherIncomeAndInterest {

    private OtherIncomeAndInterest() {}

    /**
     * Result of other income and interest deduction calculation.
     */
    public record OtherIncomeResult(Money totalOtherIncome, Money interestDeduction, String interestSectionCode) {

        public OtherIncomeResult {
            Objects.requireNonNull(totalOtherIncome, "totalOtherIncome must not be null");
            Objects.requireNonNull(interestDeduction, "interestDeduction must not be null");
            Objects.requireNonNull(interestSectionCode, "interestSectionCode must not be null");
        }
    }

    /**
     * Computes total other income and statutory interest deductions (80TTA or 80TTB).
     */
    public static OtherIncomeResult calculate(
            List<EmployeeInvOtherIncome> otherIncomeRows,
            AgeCategory ageCategory,
            OtherIncomeRule rule80Tta,
            OtherIncomeRule rule80Ttb) {

        AgeCategory category = ageCategory != null ? ageCategory : AgeCategory.GENERAL;

        Money totalOtherIncome = Money.ZERO;
        Money savingsInterest = Money.ZERO;
        Money fdInterest = Money.ZERO;

        if (otherIncomeRows != null) {
            for (EmployeeInvOtherIncome row : otherIncomeRows) {
                Money amount = Money.of(row.getAmount());
                totalOtherIncome = totalOtherIncome.add(amount);

                if (row.getKind() == OtherIncomeKind.SAVINGS_INTEREST) {
                    savingsInterest = savingsInterest.add(amount);
                } else if (row.getKind() == OtherIncomeKind.FD_INTEREST) {
                    fdInterest = fdInterest.add(amount);
                }
            }
        }

        Money interestDeduction = Money.ZERO;
        String sectionCode = "NONE";

        if (category == AgeCategory.SENIOR || category == AgeCategory.SUPER_SENIOR) {
            sectionCode = "80TTB";
            Money qualifyingInterest = savingsInterest.add(fdInterest);
            if (rule80Ttb != null && rule80Ttb.maxLimit() != null) {
                interestDeduction = qualifyingInterest.compareTo(rule80Ttb.maxLimit()) > 0
                        ? rule80Ttb.maxLimit()
                        : qualifyingInterest;
            } else {
                interestDeduction = qualifyingInterest;
            }
        } else {
            sectionCode = "80TTA";
            Money qualifyingInterest = savingsInterest;
            if (rule80Tta != null && rule80Tta.maxLimit() != null) {
                interestDeduction = qualifyingInterest.compareTo(rule80Tta.maxLimit()) > 0
                        ? rule80Tta.maxLimit()
                        : qualifyingInterest;
            } else {
                interestDeduction = qualifyingInterest;
            }
        }

        return new OtherIncomeResult(totalOtherIncome, interestDeduction, sectionCode);
    }
}
