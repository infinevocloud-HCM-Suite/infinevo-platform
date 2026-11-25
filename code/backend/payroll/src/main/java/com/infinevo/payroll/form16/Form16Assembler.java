package com.infinevo.payroll.form16;

import com.infinevo.payroll.taxcalc.recalc.TaxComputationRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Assembler utility for computing quarters, balances, assessment years,
 * and breakdown reconciliation for Form 16 statements (W-36.4).
 */
public final class Form16Assembler {

    private static final Pattern FY_PATTERN = Pattern.compile("^([0-9]{4})-([0-9]{4})$");

    /** Nothing beyond the paid quarters. */
    private static final Extras NO_EXTRAS = new Extras(null, null, false);

    private Form16Assembler() {}

    /**
     * What D-150 adds to the statement beyond the paid quarters.
     *
     * @param approvedNotPaidByPeriod tax on {@code APPROVED} regular runs not yet {@code PAID}, by period;
     *     printed as its own line and never added to the tax deducted
     * @param incomeFromSalary the tax engine's own old-regime income from salary, when known; it replaces
     *     the figure worked back from a gross total income that was floored at zero
     * @param noTdsRecord true when the employee has no TDS record for the year: the tax is nil and the
     *     statement says so
     */
    public record Extras(
            Map<String, BigDecimal> approvedNotPaidByPeriod, BigDecimal incomeFromSalary, boolean noTdsRecord) {}

    /**
     * Calculates the assessment year for a given financial year (e.g., 2026-2027 -> 2027-2028).
     */
    public static String calculateAssessmentYear(String financialYear) {
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Matcher matcher = FY_PATTERN.matcher(financialYear.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid financial year format: " + financialYear);
        }
        int startYear = Integer.parseInt(matcher.group(1));
        int endYear = Integer.parseInt(matcher.group(2));
        return (startYear + 1) + "-" + (endYear + 1);
    }

    /**
     * Assembles the statement from run tax and imported tax (W-38.2 §3).
     *
     * <p>Each quarter adds the imported TDS of its months to the {@code PAID} run tax. A month counts
     * as covered for {@code final} when it has a {@code PAID} regular run or an imported row for the
     * tenant (§13 decision 3).
     */
    public static Form16Statement assemble(
            String financialYear,
            DeductorDetails deductor,
            EmployeeDetails employee,
            String regime,
            BigDecimal annualTax,
            Map<String, BigDecimal> paidTaxByPeriod,
            Map<String, BigDecimal> importedTaxByPeriod,
            TaxComputationRecord computation,
            Set<String> paidRegularPeriods,
            Set<String> importedPeriods,
            Instant generatedAt) {
        return assemble(
                financialYear,
                deductor,
                employee,
                regime,
                annualTax,
                paidTaxByPeriod,
                importedTaxByPeriod,
                computation,
                paidRegularPeriods,
                importedPeriods,
                NO_EXTRAS,
                generatedAt);
    }

    /** As above, with the D-150 extras. */
    public static Form16Statement assemble(
            String financialYear,
            DeductorDetails deductor,
            EmployeeDetails employee,
            String regime,
            BigDecimal annualTax,
            Map<String, BigDecimal> paidTaxByPeriod,
            Map<String, BigDecimal> importedTaxByPeriod,
            TaxComputationRecord computation,
            Set<String> paidRegularPeriods,
            Set<String> importedPeriods,
            Extras extras,
            Instant generatedAt) {
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Map<String, BigDecimal> merged = new HashMap<>();
        if (paidTaxByPeriod != null) {
            paidTaxByPeriod.forEach((period, amount) -> {
                if (period != null && amount != null) {
                    merged.merge(period, amount, BigDecimal::add);
                }
            });
        }
        if (importedTaxByPeriod != null) {
            importedTaxByPeriod.forEach((period, amount) -> {
                if (period != null && amount != null) {
                    merged.merge(period, amount, BigDecimal::add);
                }
            });
        }

        Set<String> covered = new HashSet<>();
        if (paidRegularPeriods != null) {
            covered.addAll(paidRegularPeriods);
        }
        if (importedPeriods != null) {
            covered.addAll(importedPeriods);
        }
        long coveredCount =
                yearPeriods(financialYear).stream().filter(covered::contains).count();

        return assemble(
                financialYear,
                deductor,
                employee,
                regime,
                annualTax,
                merged,
                computation,
                coveredCount,
                extras == null ? NO_EXTRAS : extras,
                generatedAt);
    }

    /** The twelve periods April..March of {@code financialYear}, as {@code YYYY-MM}. */
    static List<String> yearPeriods(String financialYear) {
        Matcher matcher = FY_PATTERN.matcher(financialYear.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid financial year format: " + financialYear);
        }
        YearMonth april = YearMonth.of(Integer.parseInt(matcher.group(1)), 4);
        return IntStream.range(0, 12)
                .mapToObj(i -> april.plusMonths(i).toString())
                .toList();
    }

    /**
     * Assembles the complete Form 16 statement from individual inputs.
     */
    public static Form16Statement assemble(
            String financialYear,
            DeductorDetails deductor,
            EmployeeDetails employee,
            String regime,
            BigDecimal annualTax,
            Map<String, BigDecimal> periodTaxMap,
            TaxComputationRecord computation,
            long paidRegularPeriodsCount,
            Instant generatedAt) {
        return assemble(
                financialYear,
                deductor,
                employee,
                regime,
                annualTax,
                periodTaxMap,
                computation,
                paidRegularPeriodsCount,
                NO_EXTRAS,
                generatedAt);
    }

    private static Form16Statement assemble(
            String financialYear,
            DeductorDetails deductor,
            EmployeeDetails employee,
            String regime,
            BigDecimal annualTax,
            Map<String, BigDecimal> periodTaxMap,
            TaxComputationRecord computation,
            long paidRegularPeriodsCount,
            Extras extras,
            Instant generatedAt) {
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(deductor, "deductor must not be null");
        Objects.requireNonNull(employee, "employee must not be null");
        Objects.requireNonNull(annualTax, "annualTax must not be null");
        // D-150: the regime may be null, when there is no TDS record and no computation to name one.
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }

        String assessmentYear = calculateAssessmentYear(financialYear);
        Matcher matcher = FY_PATTERN.matcher(financialYear.trim());
        matcher.matches();
        String startYear = matcher.group(1);
        String endYear = matcher.group(2);

        // Compute Q1: April, May, June
        BigDecimal q1 = sumPeriods(periodTaxMap, List.of(startYear + "-04", startYear + "-05", startYear + "-06"));

        // Compute Q2: July, August, September
        BigDecimal q2 = sumPeriods(periodTaxMap, List.of(startYear + "-07", startYear + "-08", startYear + "-09"));

        // Compute Q3: October, November, December
        BigDecimal q3 = sumPeriods(periodTaxMap, List.of(startYear + "-10", startYear + "-11", startYear + "-12"));

        // Compute Q4: January, February, March
        BigDecimal q4 = sumPeriods(periodTaxMap, List.of(endYear + "-01", endYear + "-02", endYear + "-03"));

        List<QuarterTax> quarters = List.of(
                new QuarterTax("Q1", q1), new QuarterTax("Q2", q2), new QuarterTax("Q3", q3), new QuarterTax("Q4", q4));

        BigDecimal totalDeducted = q1.add(q2).add(q3).add(q4);
        BigDecimal balance = annualTax.subtract(totalDeducted);

        // D-150: tax on approved regular runs not yet paid, shown on its own and never counted as deducted
        BigDecimal approvedNotPaid = sumPeriods(extras.approvedNotPaidByPeriod(), yearPeriods(financialYear));

        // Final flag: true iff all 12 periods April..March have a PAID regular run
        boolean isFinal = paidRegularPeriodsCount >= 12;

        // Breakdown reconciliation
        TaxBreakdown breakdown = null;
        String breakdownNote = null;
        String statementNote = null;

        if (extras.noTdsRecord()) {
            // D-150: no TDS record means nil tax; a computation's working would not add up to it.
            statementNote = "NO_TDS_RECORD";
            breakdownNote = "NO_TDS_RECORD";
        } else if (computation != null) {
            BigDecimal compAnnualTax = computation.getAnnualTax();
            if (compAnnualTax != null && compAnnualTax.compareTo(annualTax) == 0) {
                breakdown = breakdownOf(computation, extras.incomeFromSalary());
            } else {
                breakdownNote = "OFFICER_OVERRIDE";
            }
        } else {
            // D-149: nobody overrode anything; there is simply no computation to print.
            breakdownNote = "NO_COMPUTATION";
        }

        return new Form16Statement(
                financialYear,
                assessmentYear,
                isFinal,
                deductor,
                employee,
                regime,
                quarters,
                totalDeducted,
                annualTax,
                balance,
                approvedNotPaid,
                breakdown,
                breakdownNote,
                statementNote,
                generatedAt);
    }

    /**
     * The Part B working, laid out so each line follows from the one above (D-149).
     *
     * <p>The stored {@code gross_total_income} is not the same figure under the two regimes: the old-regime
     * calculator stores it after HRA, standard deduction and professional tax, the new-regime calculator
     * before the standard deduction. Both already hold house property and other income. Gross salary and
     * income from salary are worked back from it, so the printed lines add up: gross salary less HRA,
     * standard deduction and professional tax is income from salary; plus house property and other income
     * is gross total income; less Chapter VI-A is taxable income (to the rupee).
     *
     * <p>D-150: the old-regime calculator floors gross total income at zero when a house-property loss is
     * larger than the rest, and working back from that zero overstates the salary by the difference. So
     * when the engine's own income from salary is known it is used instead, and when the heads then sum
     * below zero the gross total income prints as zero with {@code grossTotalIncomeFloored} set, so the
     * page can say why line 8 is not the sum of the lines above. A known figure that does not agree with
     * the stored total is ignored.
     *
     * <p>The stored {@code chapter_via} holds the investment deductions only. Taxable income also takes off
     * the savings-interest deduction (80TTA/80TTB) and the extra home-loan interest (80EE/80EEA), which the
     * row does not store. So the deductions line prints what was actually taken off, gross total income
     * less taxable income, whenever that is more than the stored figure by a rupee or more.
     */
    static TaxBreakdown breakdownOf(TaxComputationRecord c) {
        return breakdownOf(c, null);
    }

    static TaxBreakdown breakdownOf(TaxComputationRecord c, BigDecimal knownIncomeFromSalary) {
        BigDecimal hra = nz(c.getHraExemption());
        BigDecimal standard = nz(c.getStandardDeduction());
        BigDecimal professionalTax = nz(c.getProfessionalTax());
        BigDecimal houseProperty = nz(c.getHousePropertyIncome());
        BigDecimal other = nz(c.getOtherIncome());
        BigDecimal stored = nz(c.getGrossTotalIncome());
        BigDecimal withoutOtherHeads = stored.subtract(houseProperty).subtract(other);

        BigDecimal grossSalary;
        BigDecimal incomeFromSalary;
        if ("OLD".equalsIgnoreCase(c.getRegime())) {
            incomeFromSalary = agreesWithStored(knownIncomeFromSalary, houseProperty, other, stored)
                    ? knownIncomeFromSalary
                    : withoutOtherHeads;
            grossSalary = incomeFromSalary.add(hra).add(standard).add(professionalTax);
        } else {
            grossSalary = withoutOtherHeads;
            incomeFromSalary = grossSalary.subtract(hra).subtract(standard).subtract(professionalTax);
        }
        BigDecimal sumOfHeads = incomeFromSalary.add(houseProperty).add(other);
        boolean floored = sumOfHeads.signum() < 0;
        BigDecimal grossTotalIncome = floored ? BigDecimal.ZERO : sumOfHeads;

        BigDecimal chapterVia = nz(c.getChapterVia());
        BigDecimal takenOff = grossTotalIncome.subtract(nz(c.getTaxableIncome()));
        if (takenOff.subtract(chapterVia).compareTo(BigDecimal.ONE) >= 0) {
            chapterVia = takenOff;
        }

        return new TaxBreakdown(
                grossSalary,
                incomeFromSalary,
                grossTotalIncome,
                floored,
                hra,
                standard,
                professionalTax,
                houseProperty,
                other,
                chapterVia,
                c.getTaxableIncome(),
                c.getTaxBeforeRebate(),
                c.getRebate(),
                c.getSurcharge(),
                c.getCess(),
                c.getPrevEmployerTds(),
                c.getAnnualTax());
    }

    /**
     * A known income from salary is trusted only when it gives back the stored total: the same figure
     * (within a rupee of rounding), or a sum below zero where the stored total is the floored zero.
     */
    private static boolean agreesWithStored(
            BigDecimal known, BigDecimal houseProperty, BigDecimal other, BigDecimal stored) {
        if (known == null) {
            return false;
        }
        BigDecimal sum = known.add(houseProperty).add(other);
        if (sum.signum() < 0) {
            return stored.signum() == 0;
        }
        return sum.subtract(stored).abs().compareTo(BigDecimal.ONE) < 0;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal sumPeriods(Map<String, BigDecimal> periodMap, List<String> periods) {
        if (periodMap == null || periodMap.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (String p : periods) {
            BigDecimal val = periodMap.get(p);
            if (val != null) {
                sum = sum.add(val);
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }
}
