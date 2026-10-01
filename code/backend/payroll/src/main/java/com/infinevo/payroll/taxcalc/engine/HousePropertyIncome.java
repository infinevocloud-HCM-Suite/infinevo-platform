package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.taxcalc.reader.model.HomeLoanRule;
import com.infinevo.payroll.taxcalc.reader.model.LetOutRule;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvLetOutProperty;
import com.infinevo.shared.money.Money;
import java.util.List;
import java.util.Objects;

/**
 * Pure engine for Section 24 and Section 71(3A) House Property income and loss calculation (W-33.2 spec ? 3 step 5).
 *
 * <p>Self-occupied home loan interest is deducted under Section 24(b). Let-out property net income/loss
 * is aggregated. Combined loss across both self-occupied and let-out properties is capped at
 * {@code maxLossSetoffLimit} (Section 71(3A)).
 */
public final class HousePropertyIncome {

    private HousePropertyIncome() {}

    /**
     * Result of house property calculation.
     *
     * @param housePropertyIncome net house property income/loss allowed after combined loss set-off cap
     * @param selfOccupiedInterest total self-occupied interest deduction under Section 24(b)
     * @param letOutNet total net income/loss from let-out properties
     * @param lossCapApplied whether the Section 71(3A) combined loss set-off cap was applied
     */
    public record HousePropertyResult(
            Money housePropertyIncome, Money selfOccupiedInterest, Money letOutNet, boolean lossCapApplied) {

        public HousePropertyResult {
            Objects.requireNonNull(housePropertyIncome, "housePropertyIncome must not be null");
            Objects.requireNonNull(selfOccupiedInterest, "selfOccupiedInterest must not be null");
            Objects.requireNonNull(letOutNet, "letOutNet must not be null");
        }
    }

    /**
     * Computes house property income/loss under the Old Tax Regime.
     */
    public static HousePropertyResult calculate(
            boolean isRepayingSelfOccupiedLoan,
            boolean hasLetOutProperty,
            HomeLoanRule rule24B,
            LetOutRule letOutRule,
            List<EmployeeInvHomeLoan> homeLoans,
            List<EmployeeInvLetOutProperty> letOutProperties) {

        Objects.requireNonNull(rule24B, "rule24B must not be null");
        Objects.requireNonNull(letOutRule, "letOutRule must not be null");

        Money selfOccupiedInterest = Money.ZERO;
        if (isRepayingSelfOccupiedLoan && homeLoans != null) {
            Money totalInterestPaid = Money.ZERO;
            for (EmployeeInvHomeLoan loan : homeLoans) {
                totalInterestPaid = totalInterestPaid.add(Money.of(loan.getInterestPaid()));
            }

            if (rule24B.maxLimit() != null && totalInterestPaid.compareTo(rule24B.maxLimit()) > 0) {
                selfOccupiedInterest = rule24B.maxLimit();
            } else {
                selfOccupiedInterest = totalInterestPaid;
            }
        }

        Money letOutNet = Money.ZERO;
        if (hasLetOutProperty && letOutProperties != null) {
            for (EmployeeInvLetOutProperty prop : letOutProperties) {
                letOutNet = letOutNet.add(Money.of(prop.getNetIncomeLoss()));
            }
        }

        // Net house property before combined cap = letOutNet - selfOccupiedInterest
        Money rawHouseProperty = letOutNet.subtract(selfOccupiedInterest);

        // Section 71(3A) combined loss set-off limit: max loss allowed is maxLossSetoffLimit
        Money maxLossLimit = letOutRule.maxLossSetoffLimit();
        Money negativeLossLimit = maxLossLimit.negate();

        boolean lossCapApplied = false;
        Money housePropertyIncome = rawHouseProperty;

        if (rawHouseProperty.compareTo(negativeLossLimit) < 0) {
            housePropertyIncome = negativeLossLimit;
            lossCapApplied = true;
        }

        return new HousePropertyResult(housePropertyIncome, selfOccupiedInterest, letOutNet, lossCapApplied);
    }
}
