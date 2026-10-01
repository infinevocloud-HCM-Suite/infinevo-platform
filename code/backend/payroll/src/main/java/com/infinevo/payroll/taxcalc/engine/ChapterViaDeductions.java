package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.taxcalc.reader.model.HomeLoanRule;
import com.infinevo.payroll.taxdeclaration.housing.EmployeeInvHomeLoan;
import com.infinevo.shared.money.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure engine for Chapter VI-A deductions under the Old Tax Regime (W-33.2 spec ? 3 step 8, ? 3a, ? 3b).
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
     * Computes Chapter VI-A deductions with a single group cap for 80C.
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
        return calculate(
                section6aItems,
                employeePf,
                vpf,
                employeeNps,
                nps1bCap,
                group80cCap != null ? java.util.Map.of("80C_GROUP", group80cCap) : java.util.Map.of(),
                homeLoans,
                rule24B,
                additionalLoanRules,
                interestDeduction,
                grossTotalIncome);
    }

    /**
     * Computes Chapter VI-A deductions with dynamic group caps per category_group_code (W-33.2).
     */
    public static ChapterViaResult calculate(
            List<DeclaredItem> section6aItems,
            Money employeePf,
            Money vpf,
            Money employeeNps,
            Money nps1bCap,
            java.util.Map<String, Money> groupCaps,
            List<EmployeeInvHomeLoan> homeLoans,
            HomeLoanRule rule24B,
            List<HomeLoanRule> additionalLoanRules,
            Money interestDeduction,
            Money grossTotalIncome) {

        Objects.requireNonNull(grossTotalIncome, "grossTotalIncome must not be null");

        Money pf = employeePf != null ? employeePf : Money.ZERO;
        Money voluntaryPf = vpf != null ? vpf : Money.ZERO;
        Money preTaxNps = employeeNps != null ? employeeNps : Money.ZERO;
        Money interestDed = interestDeduction != null ? interestDeduction : Money.ZERO;

        List<ChapterViaLine> lines = new ArrayList<>();

        // Extract declared 80CCD(1B) items to apply a single combined cap across salary structure NPS and declared NPS
        Money declaredNps1b = Money.ZERO;
        List<DeclaredItem> otherSection6aItems = new ArrayList<>();
        if (section6aItems != null) {
            for (DeclaredItem item : section6aItems) {
                if ("80CCD(1B)".equalsIgnoreCase(item.sectionCode())) {
                    declaredNps1b =
                            declaredNps1b.add(item.declaredAmount() != null ? item.declaredAmount() : Money.ZERO);
                } else {
                    otherSection6aItems.add(item);
                }
            }
        }

        // 1. Employee NPS split (80CCD(1B) first up to cap across pre-tax and declared, remainder into 80C group)
        Money totalNps = preTaxNps.add(declaredNps1b);
        Money nps1bAllowed = Money.ZERO;
        Money nps80cRemainder = Money.ZERO;
        if (!totalNps.isZero()) {
            if (nps1bCap != null && totalNps.compareTo(nps1bCap) > 0) {
                nps1bAllowed = nps1bCap;
                nps80cRemainder = totalNps.subtract(nps1bCap);
            } else {
                nps1bAllowed = totalNps;
                nps80cRemainder = Money.ZERO;
            }
            lines.add(new ChapterViaLine(
                    "80CCD(1B)", "NPS employee additional contribution", totalNps, nps1bAllowed, nps1bCap));
            if (!nps80cRemainder.isZero()) {
                lines.add(new ChapterViaLine(
                        "80CCD(1)", "NPS employee contribution (80C group)", nps80cRemainder, nps80cRemainder, null));
            }
        }

        // 2. Home loan principal into 80C group, and excess interest under 80EE/80EEA across total aggregate loans
        Money totalHomeLoanPrincipal = Money.ZERO;
        Money totalFirstTimeBuyerInterest = Money.ZERO;
        LocalDate eligibleSanctionDate = null;

        if (homeLoans != null) {
            for (EmployeeInvHomeLoan loan : homeLoans) {
                if (loan.getPrincipalPaid() != null) {
                    totalHomeLoanPrincipal = totalHomeLoanPrincipal.add(Money.of(loan.getPrincipalPaid()));
                }
                if (loan.isFirstTimeBuyer() && loan.getInterestPaid() != null) {
                    totalFirstTimeBuyerInterest = totalFirstTimeBuyerInterest.add(Money.of(loan.getInterestPaid()));
                    if (eligibleSanctionDate == null && loan.getLoanSanctionedOn() != null) {
                        eligibleSanctionDate = loan.getLoanSanctionedOn();
                    }
                }
            }
        }

        Money additionalHomeLoanInterest = Money.ZERO;
        Money cap24b = rule24B != null ? rule24B.maxLimit() : null;
        if (cap24b != null && totalFirstTimeBuyerInterest.compareTo(cap24b) > 0) {
            Money excessInterest = totalFirstTimeBuyerInterest.subtract(cap24b);
            if (additionalLoanRules != null && eligibleSanctionDate != null) {
                for (HomeLoanRule rule : additionalLoanRules) {
                    if (rule.isSanctionDateEligible(eligibleSanctionDate)) {
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

        // 4. Declared 6A items (group items by category_group_code, separate non-group items)
        java.util.Map<String, Money> groupSums = new java.util.LinkedHashMap<>();
        Money nonGroupItemsSum = Money.ZERO;

        for (DeclaredItem item : otherSection6aItems) {
            Money declared = item.declaredAmount() != null ? item.declaredAmount() : Money.ZERO;
            Money allowed =
                    (item.maxLimit() != null && declared.compareTo(item.maxLimit()) > 0) ? item.maxLimit() : declared;

            String group = item.categoryGroupCode();
            if (group != null && !group.isBlank()) {
                groupSums.put(group, groupSums.getOrDefault(group, Money.ZERO).add(allowed));
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

        // 5. Total 80C group components
        Money total80cRaw = groupSums
                .getOrDefault("80C_GROUP", Money.ZERO)
                .add(pf)
                .add(voluntaryPf)
                .add(totalHomeLoanPrincipal)
                .add(nps80cRemainder);
        groupSums.put("80C_GROUP", total80cRaw);

        // Apply group caps across all category_group_code groups
        Money totalGroupsAllowed = Money.ZERO;
        Money group80cAllowed = Money.ZERO;

        for (java.util.Map.Entry<String, Money> entry : groupSums.entrySet()) {
            String group = entry.getKey();
            Money rawSum = entry.getValue();
            Money cap = groupCaps != null ? groupCaps.get(group) : null;

            Money allowedGroup = (cap != null && rawSum.compareTo(cap) > 0) ? cap : rawSum;
            totalGroupsAllowed = totalGroupsAllowed.add(allowedGroup);

            if ("80C_GROUP".equals(group)) {
                group80cAllowed = allowedGroup;
            }

            lines.add(new ChapterViaLine(
                    group,
                    "80C_GROUP".equals(group)
                            ? "Section 80CCE group cap (80C, 80CCC, 80CCD(1))"
                            : "Group cap (" + group + ")",
                    rawSum,
                    allowedGroup,
                    cap));
        }

        // 6. Chapter VI-A investment deductions = all groups allowed + NPS 1B + non-group items
        Money chapterViaInvestments = totalGroupsAllowed.add(nps1bAllowed).add(nonGroupItemsSum);

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
