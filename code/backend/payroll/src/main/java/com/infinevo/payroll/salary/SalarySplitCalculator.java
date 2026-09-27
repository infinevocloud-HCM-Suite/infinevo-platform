package com.infinevo.payroll.salary;

import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.PercentageOf;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side salary split calculator resolving component amounts and validating CTC balance (W-26.2).
 */
public final class SalarySplitCalculator {

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal MONTHS_IN_YEAR = BigDecimal.valueOf(12);
    public static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private SalarySplitCalculator() {}

    public record ComponentInput(
            UUID componentId,
            String code,
            String name,
            CalculationType calculationType,
            BigDecimal value,
            PercentageOf percentageOf,
            boolean includedInCtc,
            String earningType,
            String earningFrequency,
            String carryForwardOption) {}

    public record CalculatedResult(
            UUID componentId,
            String code,
            String name,
            CalculationType calculationType,
            BigDecimal value,
            PercentageOf percentageOf,
            BigDecimal monthlyAmount,
            BigDecimal annualAmount,
            boolean includedInCtc,
            String earningFrequency,
            String carryForwardOption) {}

    public record SplitOutput(
            BigDecimal annualCtc,
            BigDecimal monthlyCtc,
            List<CalculatedResult> earnings,
            List<CalculatedResult> benefits,
            List<CalculatedResult> reimbursements) {}

    /**
     * Resolves all earnings, benefits, and reimbursements against the provided annual CTC.
     */
    public static SplitOutput calculate(
            BigDecimal annualCtc,
            List<ComponentInput> earnings,
            List<ComponentInput> benefits,
            List<ComponentInput> reimbursements) {

        if (annualCtc == null || annualCtc.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SalaryValidationException("annualCtc", "Annual CTC must be greater than zero");
        }

        Money annualCtcMoney = Money.of(annualCtc);
        BigDecimal monthlyCtc = annualCtcMoney.raw().divide(MONTHS_IN_YEAR, 4, ROUNDING);

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // Validate basic rules on inputs
        validateInputs("earnings", earnings, fieldErrors);
        validateInputs("benefits", benefits, fieldErrors);
        validateInputs("reimbursements", reimbursements, fieldErrors);

        if (!fieldErrors.isEmpty()) {
            throw new SalaryValidationException(fieldErrors);
        }

        // 1. Resolve earnings (dependency order: FLAT/% of CTC -> % of BASIC -> % of GROSS)
        List<CalculatedResult> resolvedEarnings = resolveEarnings(annualCtc, earnings);

        // 2. Compute Gross from resolved earnings
        BigDecimal grossAnnual = BigDecimal.ZERO.setScale(4, ROUNDING);
        BigDecimal grossMonthly = BigDecimal.ZERO.setScale(4, ROUNDING);
        for (CalculatedResult e : resolvedEarnings) {
            grossAnnual = grossAnnual.add(e.annualAmount());
            grossMonthly = grossMonthly.add(e.monthlyAmount());
        }

        // Find basic earning if present
        CalculatedResult basicEarning = findBasicEarning(resolvedEarnings);

        // 3. Resolve benefits
        List<CalculatedResult> resolvedBenefits =
                resolveNonEarnings(annualCtc, basicEarning, grossAnnual, grossMonthly, benefits);

        // 4. Resolve reimbursements
        List<CalculatedResult> resolvedReimbursements =
                resolveNonEarnings(annualCtc, basicEarning, grossAnnual, grossMonthly, reimbursements);

        // 5. Check sum of is_included_in_ctc equals annual_ctc at scale 2
        BigDecimal sumIncludedAnnual = BigDecimal.ZERO.setScale(4, ROUNDING);
        for (CalculatedResult e : resolvedEarnings) {
            if (e.includedInCtc()) {
                sumIncludedAnnual = sumIncludedAnnual.add(e.annualAmount());
            }
        }
        for (CalculatedResult b : resolvedBenefits) {
            if (b.includedInCtc()) {
                sumIncludedAnnual = sumIncludedAnnual.add(b.annualAmount());
            }
        }
        for (CalculatedResult r : resolvedReimbursements) {
            if (r.includedInCtc()) {
                sumIncludedAnnual = sumIncludedAnnual.add(r.annualAmount());
            }
        }

        BigDecimal sumScale2 = sumIncludedAnnual.setScale(2, ROUNDING);
        BigDecimal ctcScale2 = annualCtc.setScale(2, ROUNDING);
        BigDecimal diff = sumScale2.subtract(ctcScale2);

        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            throw new SalaryValidationException(
                    "annualCtc",
                    "The annual sum of components included in CTC (" + sumScale2
                            + ") does not equal annual CTC (" + ctcScale2
                            + "). Difference: " + diff.abs());
        }

        return new SplitOutput(
                annualCtc.setScale(4, ROUNDING),
                monthlyCtc,
                resolvedEarnings,
                resolvedBenefits,
                resolvedReimbursements);
    }

    private static void validateInputs(String category, List<ComponentInput> list, Map<String, String> errors) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            ComponentInput item = list.get(i);
            String prefix = category + "[" + i + "].";
            if (item.componentId() == null) {
                errors.put(prefix + "componentId", "Component ID is required");
            }
            if (item.value() == null || item.value().compareTo(BigDecimal.ZERO) < 0) {
                errors.put(prefix + "value", "Component value must not be negative");
            }
            CalculationType calcType = item.calculationType() != null ? item.calculationType() : CalculationType.FLAT;
            if (calcType == CalculationType.PERCENTAGE) {
                if (item.percentageOf() == null) {
                    errors.put(
                            prefix + "percentageOf", "percentage_of is required when calculation_type is PERCENTAGE");
                }
            } else if (calcType == CalculationType.FLAT) {
                if (item.percentageOf() != null) {
                    errors.put(prefix + "percentageOf", "percentage_of must be null when calculation_type is FLAT");
                }
            }
        }
    }

    private static List<CalculatedResult> resolveEarnings(BigDecimal annualCtc, List<ComponentInput> earnings) {
        if (earnings == null || earnings.isEmpty()) {
            return List.of();
        }

        List<CalculatedResult> results = new ArrayList<>();
        Map<ComponentInput, CalculatedResult> resolvedMap = new LinkedHashMap<>();

        // Phase 1: resolve FLAT and % of CTC
        for (ComponentInput e : earnings) {
            CalculationType calcType = e.calculationType() != null ? e.calculationType() : CalculationType.FLAT;
            if (calcType == CalculationType.FLAT) {
                BigDecimal monthly = e.value().setScale(4, ROUNDING);
                BigDecimal annual = monthly.multiply(MONTHS_IN_YEAR).setScale(4, ROUNDING);
                CalculatedResult res = toResult(e, monthly, annual);
                resolvedMap.put(e, res);
            } else if (calcType == CalculationType.PERCENTAGE && e.percentageOf() == PercentageOf.CTC) {
                BigDecimal factor = e.value().divide(ONE_HUNDRED, 6, ROUNDING);
                BigDecimal annual = annualCtc.multiply(factor).setScale(4, ROUNDING);
                BigDecimal monthly = annual.divide(MONTHS_IN_YEAR, 4, ROUNDING);
                CalculatedResult res = toResult(e, monthly, annual);
                resolvedMap.put(e, res);
            }
        }

        // Find Basic Earning from Phase 1 if present
        CalculatedResult basicEarning = findBasicInMap(resolvedMap);

        // Phase 2: resolve % of BASIC
        for (ComponentInput e : earnings) {
            CalculationType calcType = e.calculationType() != null ? e.calculationType() : CalculationType.FLAT;
            if (calcType == CalculationType.PERCENTAGE && e.percentageOf() == PercentageOf.BASIC) {
                if (basicEarning == null) {
                    throw new SalaryValidationException(
                            "percentageOf",
                            "BASIC percentage_of requires an earning with code or type 'BASIC' in the same version");
                }
                BigDecimal factor = e.value().divide(ONE_HUNDRED, 6, ROUNDING);
                BigDecimal annual = basicEarning.annualAmount().multiply(factor).setScale(4, ROUNDING);
                BigDecimal monthly =
                        basicEarning.monthlyAmount().multiply(factor).setScale(4, ROUNDING);
                CalculatedResult res = toResult(e, monthly, annual);
                resolvedMap.put(e, res);
            }
        }

        // Compute subtotal Gross of Phase 1 + Phase 2
        BigDecimal partialGrossAnnual = BigDecimal.ZERO.setScale(4, ROUNDING);
        BigDecimal partialGrossMonthly = BigDecimal.ZERO.setScale(4, ROUNDING);
        for (CalculatedResult r : resolvedMap.values()) {
            partialGrossAnnual = partialGrossAnnual.add(r.annualAmount());
            partialGrossMonthly = partialGrossMonthly.add(r.monthlyAmount());
        }

        // Phase 3: resolve % of GROSS
        for (ComponentInput e : earnings) {
            CalculationType calcType = e.calculationType() != null ? e.calculationType() : CalculationType.FLAT;
            if (calcType == CalculationType.PERCENTAGE && e.percentageOf() == PercentageOf.GROSS) {
                BigDecimal factor = e.value().divide(ONE_HUNDRED, 6, ROUNDING);
                BigDecimal annual = partialGrossAnnual.multiply(factor).setScale(4, ROUNDING);
                BigDecimal monthly = partialGrossMonthly.multiply(factor).setScale(4, ROUNDING);
                CalculatedResult res = toResult(e, monthly, annual);
                resolvedMap.put(e, res);
            }
        }

        for (ComponentInput e : earnings) {
            CalculatedResult r = resolvedMap.get(e);
            if (r != null) {
                results.add(r);
            }
        }
        return results;
    }

    private static List<CalculatedResult> resolveNonEarnings(
            BigDecimal annualCtc,
            CalculatedResult basicEarning,
            BigDecimal grossAnnual,
            BigDecimal grossMonthly,
            List<ComponentInput> list) {

        if (list == null || list.isEmpty()) {
            return List.of();
        }

        List<CalculatedResult> results = new ArrayList<>();
        for (ComponentInput item : list) {
            CalculationType calcType = item.calculationType() != null ? item.calculationType() : CalculationType.FLAT;
            BigDecimal monthly;
            BigDecimal annual;

            if (calcType == CalculationType.FLAT) {
                monthly = item.value().setScale(4, ROUNDING);
                annual = monthly.multiply(MONTHS_IN_YEAR).setScale(4, ROUNDING);
            } else if (item.percentageOf() == PercentageOf.CTC) {
                BigDecimal factor = item.value().divide(ONE_HUNDRED, 6, ROUNDING);
                annual = annualCtc.multiply(factor).setScale(4, ROUNDING);
                monthly = annual.divide(MONTHS_IN_YEAR, 4, ROUNDING);
            } else if (item.percentageOf() == PercentageOf.BASIC) {
                if (basicEarning == null) {
                    throw new SalaryValidationException(
                            "percentageOf",
                            "BASIC percentage_of requires an earning with code or type 'BASIC' in the same version");
                }
                BigDecimal factor = item.value().divide(ONE_HUNDRED, 6, ROUNDING);
                annual = basicEarning.annualAmount().multiply(factor).setScale(4, ROUNDING);
                monthly = basicEarning.monthlyAmount().multiply(factor).setScale(4, ROUNDING);
            } else if (item.percentageOf() == PercentageOf.GROSS) {
                BigDecimal factor = item.value().divide(ONE_HUNDRED, 6, ROUNDING);
                annual = grossAnnual.multiply(factor).setScale(4, ROUNDING);
                monthly = grossMonthly.multiply(factor).setScale(4, ROUNDING);
            } else {
                throw new SalaryValidationException(
                        "percentageOf", "Unsupported percentage_of: " + item.percentageOf());
            }

            results.add(toResult(item, monthly, annual));
        }
        return results;
    }

    private static CalculatedResult findBasicInMap(Map<ComponentInput, CalculatedResult> map) {
        for (Map.Entry<ComponentInput, CalculatedResult> entry : map.entrySet()) {
            if (isBasic(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static CalculatedResult findBasicEarning(List<CalculatedResult> earnings) {
        for (CalculatedResult e : earnings) {
            if ("BASIC".equalsIgnoreCase(e.code())) {
                return e;
            }
        }
        return null;
    }

    private static boolean isBasic(ComponentInput input) {
        return "BASIC".equalsIgnoreCase(input.code()) || "BASIC".equalsIgnoreCase(input.earningType());
    }

    private static CalculatedResult toResult(ComponentInput input, BigDecimal monthly, BigDecimal annual) {
        return new CalculatedResult(
                input.componentId(),
                input.code(),
                input.name(),
                input.calculationType() != null ? input.calculationType() : CalculationType.FLAT,
                input.value(),
                input.percentageOf(),
                monthly,
                annual,
                input.includedInCtc(),
                input.earningFrequency(),
                input.carryForwardOption());
    }
}
