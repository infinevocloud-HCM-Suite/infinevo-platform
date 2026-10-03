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

    private Form16Assembler() {}

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
                financialYear, deductor, employee, regime, annualTax, merged, computation, coveredCount, generatedAt);
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
        Objects.requireNonNull(financialYear, "financialYear must not be null");
        Objects.requireNonNull(deductor, "deductor must not be null");
        Objects.requireNonNull(employee, "employee must not be null");
        Objects.requireNonNull(regime, "regime must not be null");
        Objects.requireNonNull(annualTax, "annualTax must not be null");
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

        // Final flag: true iff all 12 periods April..March have a PAID regular run
        boolean isFinal = paidRegularPeriodsCount >= 12;

        // Breakdown reconciliation
        TaxBreakdown breakdown = null;
        String breakdownNote = null;

        if (computation != null) {
            BigDecimal compAnnualTax = computation.getAnnualTax();
            if (compAnnualTax != null && compAnnualTax.compareTo(annualTax) == 0) {
                breakdown = new TaxBreakdown(
                        computation.getGrossTotalIncome(),
                        computation.getHraExemption(),
                        computation.getStandardDeduction(),
                        computation.getProfessionalTax(),
                        computation.getHousePropertyIncome(),
                        computation.getOtherIncome(),
                        computation.getChapterVia(),
                        computation.getTaxableIncome(),
                        computation.getTaxBeforeRebate(),
                        computation.getRebate(),
                        computation.getSurcharge(),
                        computation.getCess(),
                        computation.getPrevEmployerTds(),
                        computation.getAnnualTax());
            } else {
                breakdownNote = "OFFICER_OVERRIDE";
            }
        } else {
            breakdownNote = "OFFICER_OVERRIDE";
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
                breakdown,
                breakdownNote,
                generatedAt);
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
