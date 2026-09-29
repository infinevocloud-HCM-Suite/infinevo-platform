package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.taxcalc.reader.model.HomeLoanRule;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.shared.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure engine for Chapter VI-A deductions under the Old Tax Regime (W-33.2 spec § 3 step 8, § 3a, § 3b).
 *
 * <p>Handles:
 * <ul>
 *   <li>Section 80C, 80CCC, and 80CCD(1) grouped under Section 80CCE group cap</li>
 *   <li>Pre-tax additions (EPF, VPF, Home loan principal, NPS remainder) into 80C group</li>
 *   <li>Section 80CCD(1B) dedicated deduction (first up to cap)</li>
 *   <li>Individual Section 6A items (80D, 80E, 80G, etc.) at item caps</li>
 *   <li>Section 80EE / 80EEA additional home loan interest for first-time buyers within sanction windows</li>
 *   <li>Aggregate limitation under Section 80A(2) capped at Gross Total Income</li>
 * </ul>
 */
public final class ChapterViaDeductions {

    private ChapterViaDeductions() {}

    /**
     * Declared Section 6A item input.
     */
    public record DeclaredItem(
            String sectionCode, String description, String categoryGroupCode, Money declaredAmount, Money maxLimit) {

        public DeclaredItem {
            Objects.requireNonNull(sectionCode, "sectionCode must not be null");
            Objects.requireNonNull(declaredAmount, "declaredAmount must not be null");
        }
    }

    /**
     * Line-by-line audit entry for Chapter VI-A working.
     */
    public record ChapterViaLine(
            String sectionCode, String description, Money declaredAmount, Money allowedAmount, Money cap) {

        public ChapterViaLine {
            Objects.requireNonNull(sectionCode, "sectionCode must not be null");
            Objects.requireNonNull(description, "description must not be null");
            Objects.requireNonNull(declaredAmount, "declaredAmount must not be null");
            Objects.requireNonNull(allowedAmount, "allowedAmount must not be null");
        }
    }

    /**
     * Result of Chapter VI-A deduction calculations.
     */
    public record ChapterViaResult(
            Money chapterViaDeductions,
            Money group80cAllowed,
            Money nps1bAllowed,
            Money additionalHomeLoanInterest,
            Money totalAllowed,
            List<ChapterViaLine> lines) {

        public ChapterViaResult {
            Objects.requireNonNull(chapterViaDeductions, "chapterViaDeductions must not be null");
            Objects.requireNonNull(group80cAllowed, "group80cAllowed must not be null");
            Objects.requireNonNull(nps1bAllowed, "nps1bAllowed must not be null");
            Objects.requireNonNull(additionalHomeLoanInterest, "additionalHomeLoanInterest must not be null");
            Objects.requireNonNull(totalAllowed, "totalAllowed must not be null");
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * Computes Chapter VI-A deductions.
     */
    public static ChapterViaResult calculate(
            List<DeclaredItem> section6aItems,
            Money employeePf,
            Money vpf,
            Money employeeNps,
            Money nps1bCap,
            Money group80cCap,
            List<EmployeeInvHomeLoan> homeLoans,
            HomeLoanRule rule24B,
            List<HomeLoanRule> additionalLoanRules,
            Money interestDeduction,
            Money grossTotalIncome) {

        Objects.requireNonNull(group80cCap, "group80cCap must not be null");
        Objects.requireNonNull(grossTotalIncome, "grossTotalIncome must not be null");

        Money pf = employeePf != null ? employeePf : Money.ZERO;
        Money voluntaryPf = vpf != null ? vpf : Money.ZERO;
        Money nps = employeeNps != null ? employeeNps : Money.ZERO;
        Money interestDed = interestDeduction != null ? interestDeduction : Money.ZERO;

        List<ChapterViaLine> lines = new ArrayList<>();

        // 1. Employee NPS split (80CCD(1B) first up to cap, remainder into 80C group)
        Money nps1bAllowed = Money.ZERO;
        Money nps80cRemainder = Money.ZERO;
        if (!nps.isZero()) {
            if (nps1bCap != null && nps.compareTo(nps1bCap) > 0) {
                nps1bAllowed = nps1bCap;
                nps80cRemainder = nps.subtract(nps1bCap);
            } else {
                nps1bAllowed = nps;
                nps80cRemainder = Money.ZERO;
            }
            lines.add(new ChapterViaLine(
                    "80CCD(1B)", "NPS employee additional contribution", nps, nps1bAllowed, nps1bCap));
            if (!nps80cRemainder.isZero()) {
                lines.add(new ChapterViaLine(
                        "80CCD(1)", "NPS employee contribution (80C group)", nps80cRemainder, nps80cRemainder, null));
            }
        }

        // 2. Home loan principal into 80C group, and excess interest under 80EE/80EEA
        Money totalHomeLoanPrincipal = Money.ZERO;
        Money additionalHomeLoanInterest = Money.ZERO;

        if (homeLoans != null) {
            for (EmployeeInvHomeLoan loan : homeLoans) {
                if (loan.getPrincipalPaid() != null) {
                    totalHomeLoanPrincipal = totalHomeLoanPrincipal.add(Money.of(loan.getPrincipalPaid()));
                }

                if (loan.isFirstTimeBuyer() && loan.getInterestPaid() != null && rule24B != null) {
                    Money loanInterest = Money.of(loan.getInterestPaid());
                    Money cap24b = rule24B.maxLimit();
                    if (cap24b != null && loanInterest.compareTo(cap24b) > 0) {
                        Money excessInterest = loanInterest.subtract(cap24b);
                        LocalDate sanctionDate = loan.getLoanSanctionedOn();

                        if (additionalLoanRules != null && sanctionDate != null) {
                            for (HomeLoanRule rule : additionalLoanRules) {
                                if (rule.isSanctionDateEligible(sanctionDate)) {
                                    Money allowedInterest =
                                            (rule.maxLimit() != null && excessInterest.compareTo(rule.maxLimit()) > 0)
                                                    ? rule.maxLimit()
                                                    : excessInterest;
                                    additionalHomeLoanInterest = additionalHomeLoanInterest.add(allowedInterest);
                                    lines.add(new ChapterViaLine(
                                            rule.sectionCode(),
                                            rule.sectionName(),
                                            excessInterest,
                                            allowedInterest,
                                            rule.maxLimit()));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!totalHomeLoanPrincipal.isZero()) {
            lines.add(new ChapterViaLine(
                    "80C", "Home loan principal paid", totalHomeLoanPrincipal, totalHomeLoanPrincipal, null));
        }

        // 3. Pre-tax additions to 80C
        if (!pf.isZero()) {
            lines.add(new ChapterViaLine("80C", "Employee Provident Fund (EPF)", pf, pf, null));
        }
        if (!voluntaryPf.isZero()) {
            lines.add(new ChapterViaLine("80C", "Voluntary Provident Fund (VPF)", voluntaryPf, voluntaryPf, null));
        }

        // 4. Declared 6A items (separate group items from non-group items)
        Money group80cItemsSum = Money.ZERO;
        Money nonGroupItemsSum = Money.ZERO;

        if (section6aItems != null) {
            for (DeclaredItem item : section6aItems) {
                Money declared = item.declaredAmount();
                Money allowed = (item.maxLimit() != null && declared.compareTo(item.maxLimit()) > 0)
                        ? item.maxLimit()
                        : declared;

                if ("80C_GROUP".equals(item.categoryGroupCode())) {
                    group80cItemsSum = group80cItemsSum.add(allowed);
                    lines.add(new ChapterViaLine(
                            item.sectionCode(),
                            item.description() != null ? item.description() : item.sectionCode(),
                            declared,
                            allowed,
                            item.maxLimit()));
                } else {
                    nonGroupItemsSum = nonGroupItemsSum.add(allowed);
                    lines.add(new ChapterViaLine(
                            item.sectionCode(),
                            item.description() != null ? item.description() : item.sectionCode(),
                            declared,
                            allowed,
                            item.maxLimit()));
                }
            }
        }

        // 5. Total 80C group capping
        Money total80cRaw = group80cItemsSum
                .add(pf)
                .add(voluntaryPf)
                .add(totalHomeLoanPrincipal)
                .add(nps80cRemainder);

        Money group80cAllowed = total80cRaw.compareTo(group80cCap) > 0 ? group80cCap : total80cRaw;

        lines.add(new ChapterViaLine(
                "80C_GROUP",
                "Section 80CCE group cap (80C, 80CCC, 80CCD(1))",
                total80cRaw,
                group80cAllowed,
                group80cCap));

        // 6. Chapter VI-A investment deductions = 80C group + NPS 1B + non-group items
        Money chapterViaInvestments = group80cAllowed.add(nps1bAllowed).add(nonGroupItemsSum);

        // 7. Aggregate limitation under Section 80A(2)
        // Total deductions = chapterViaInvestments + interestDeduction + additionalHomeLoanInterest
        Money totalBeforeGtiCap = chapterViaInvestments.add(interestDed).add(additionalHomeLoanInterest);

        Money effectiveGti = grossTotalIncome.isNegative() ? Money.ZERO : grossTotalIncome;
        Money totalAllowed = totalBeforeGtiCap.compareTo(effectiveGti) > 0 ? effectiveGti : totalBeforeGtiCap;

        // If total exceeded GTI, adjust chapterViaInvestments so the components sum to totalAllowed
        Money effectiveInvestments = chapterViaInvestments;
        if (totalBeforeGtiCap.compareTo(effectiveGti) > 0) {
            Money remaining = effectiveGti.subtract(interestDed).subtract(additionalHomeLoanInterest);
            effectiveInvestments = remaining.isNegative() ? Money.ZERO : remaining;
        }

        return new ChapterViaResult(
                effectiveInvestments, group80cAllowed, nps1bAllowed, additionalHomeLoanInterest, totalAllowed, lines);
    }
}
