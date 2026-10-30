package com.infinevo.payroll.salary;

import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.PercentageOf;
import com.infinevo.payroll.statutory.lines.DerivedStatutoryLine;
import com.infinevo.payroll.statutory.lines.StatutoryLineDeriver;
import com.infinevo.payroll.statutory.settings.EpfSetting;
import com.infinevo.payroll.statutory.settings.EsiSetting;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, ROUNDING);

    /** Passes allowed to settle rounding leftovers into the balancing earning; one or two suffice in practice. */
    private static final int MAX_BALANCING_PASSES = 8;

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
            String carryForwardOption,
            boolean balancing,
            boolean fbpComponent,
            boolean variable) {

        public ComponentInput(
                UUID componentId,
                String code,
                String name,
                CalculationType calculationType,
                BigDecimal value,
                PercentageOf percentageOf,
                boolean includedInCtc,
                String earningType,
                String earningFrequency,
                String carryForwardOption) {
            this(
                    componentId,
                    code,
                    name,
                    calculationType,
                    value,
                    percentageOf,
                    includedInCtc,
                    earningType,
                    earningFrequency,
                    carryForwardOption,
                    false,
                    false,
                    false);
        }

        ComponentInput withValue(BigDecimal newValue) {
            return new ComponentInput(
                    componentId,
                    code,
                    name,
                    calculationType,
                    newValue,
                    percentageOf,
                    includedInCtc,
                    earningType,
                    earningFrequency,
                    carryForwardOption,
                    balancing,
                    fbpComponent,
                    variable);
        }
    }

    /**
     * The inputs {@link StatutoryLineDeriver#derive} needs beyond the split itself (W-31.3, W-75.1).
     */
    public record StatutoryInputs(
            EmployeeStatutoryProfile profile,
            EpfSetting epf,
            EsiSetting esi,
            LocalDate dateOfBirth,
            LocalDate effectiveFrom) {}

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
            String carryForwardOption,
            String earningType) {

        public CalculatedResult(
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
                String carryForwardOption) {
            this(
                    componentId,
                    code,
                    name,
                    calculationType,
                    value,
                    percentageOf,
                    monthlyAmount,
                    annualAmount,
                    includedInCtc,
                    earningFrequency,
                    carryForwardOption,
                    null);
        }
    }

    public record SplitOutput(
            BigDecimal annualCtc,
            BigDecimal monthlyCtc,
            List<CalculatedResult> earnings,
            List<CalculatedResult> benefits,
            List<CalculatedResult> reimbursements) {}

    /**
     * Resolves all earnings, benefits, and reimbursements against the provided annual CTC without CTC balance validation.
     */
    public static SplitOutput resolve(
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

        return new SplitOutput(
                annualCtc.setScale(4, ROUNDING),
                monthlyCtc,
                resolvedEarnings,
                resolvedBenefits,
                resolvedReimbursements);
    }

    /**
     * Validates that the sum of components included in CTC (plus any additional statutory amounts included in CTC)
     * matches the annual CTC at scale 2.
     */
    public static void validateCtcBalance(
            BigDecimal annualCtc, SplitOutput split, BigDecimal additionalIncludedAnnual) {

        BigDecimal sumIncludedAnnual = includedAnnual(split, additionalIncludedAnnual);

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
    }

    /**
     * Resolves all earnings, benefits, and reimbursements against the provided annual CTC and validates CTC balance.
     */
    public static SplitOutput calculate(
            BigDecimal annualCtc,
            List<ComponentInput> earnings,
            List<ComponentInput> benefits,
            List<ComponentInput> reimbursements) {
        SplitOutput split = resolve(annualCtc, earnings, benefits, reimbursements);
        validateCtcBalance(annualCtc, split, BigDecimal.ZERO);
        return split;
    }

    /**
     * Resolves the split like {@link #resolve} and, when one earning is marked balancing, computes that earning's
     * monthly amount {@code B} so the in-CTC total, employer statutory lines included, equals the CTC (W-75.1).
     *
     * <p>{@code B} = monthly CTC − other in-CTC components − in-CTC employer EPF/EPS/EDLI/admin (these depend on
     * Basic only, so are fixed first). When ESI applies and employer ESI is in CTC, ESI depends on gross, which
     * contains {@code B}: {@code B = (M − others − PFer − r·E0) / (1 + r)}; if that gross exceeds the ESI ceiling,
     * ESI does not apply and {@code B} is recomputed without it. Any paisa left after rounding goes into {@code B}.
     * Ported from the browser rule in {@code legacy/Payroll-Fend-react/src/pages/mainPages/employee/salaryDetails.js:392-396}.
     * Without a balancing earning the result is exactly {@link #resolve}.
     */
    public static SplitOutput resolveWithBalancing(
            BigDecimal annualCtc,
            List<ComponentInput> earnings,
            List<ComponentInput> benefits,
            List<ComponentInput> reimbursements,
            StatutoryInputs statutory) {

        int index = findBalancingIndex(earnings, benefits, reimbursements);
        if (index < 0) {
            return resolve(annualCtc, earnings, benefits, reimbursements);
        }

        // The sent value is ignored: resolve everything else with the balancing earning at zero.
        SplitOutput base = resolve(annualCtc, withBalancingValue(earnings, index, ZERO), benefits, reimbursements);
        List<DerivedStatutoryLine> baseLines = deriveStatutory(base, statutory);

        BigDecimal ctc = annualCtc.setScale(4, ROUNDING);
        BigDecimal remainingAnnual = ctc.subtract(includedAnnual(base, includedStatutoryAnnual(baseLines)));
        if (remainingAnnual.signum() < 0) {
            throw exceedsCtc(remainingAnnual.negate());
        }

        BigDecimal employerPfMonthly = ZERO;
        for (DerivedStatutoryLine line : baseLines) {
            if (line.isEpf() && line.includedInCtc()) {
                employerPfMonthly = employerPfMonthly.add(line.monthlyAmount().raw());
            }
        }
        BigDecimal balance = base.monthlyCtc()
                .subtract(includedMonthly(base))
                .subtract(employerPfMonthly)
                .setScale(4, ROUNDING);

        EsiSetting esi = statutory != null ? statutory.esi() : null;
        if (esiApplies(statutory) && esi.isIncludeEmployerInCtc()) {
            BigDecimal rate = esi.getEmployerRate().divide(ONE_HUNDRED, 6, ROUNDING);
            BigDecimal grossWithoutBalance = grossMonthly(base);
            BigDecimal withEsi =
                    balance.subtract(rate.multiply(grossWithoutBalance)).divide(BigDecimal.ONE.add(rate), 4, ROUNDING);
            if (grossWithoutBalance.add(withEsi).compareTo(esi.getWageCeiling()) <= 0) {
                balance = withEsi;
            }
        }
        if (balance.signum() < 0) {
            balance = ZERO;
        }

        // Re-resolve with B and push any paisa left over by rounding into B, so validateCtcBalance passes exactly.
        SplitOutput split = null;
        for (int pass = 0; pass < MAX_BALANCING_PASSES; pass++) {
            split = resolve(annualCtc, withBalancingValue(earnings, index, balance), benefits, reimbursements);
            BigDecimal included = includedAnnual(split, includedStatutoryAnnual(deriveStatutory(split, statutory)));
            if (included.setScale(2, ROUNDING).compareTo(annualCtc.setScale(2, ROUNDING)) == 0) {
                break;
            }
            balance = balance.add(ctc.subtract(included).divide(MONTHS_IN_YEAR, 4, ROUNDING));
            if (balance.signum() < 0) {
                throw exceedsCtc(included.subtract(ctc));
            }
        }
        return split;
    }

    /**
     * Derives the statutory EPF and ESI lines for a resolved split, exactly as a saved version does (W-31.3).
     */
    public static List<DerivedStatutoryLine> deriveStatutory(SplitOutput split, StatutoryInputs statutory) {
        CalculatedResult basicResult = findBasicEarning(split.earnings());
        Money basicMonthly = basicResult != null ? Money.of(basicResult.monthlyAmount()) : Money.ZERO;
        Money grossMonthly = Money.of(grossMonthly(split));
        if (statutory == null) {
            return StatutoryLineDeriver.derive(basicMonthly, grossMonthly, null, null, null, null, null);
        }
        return StatutoryLineDeriver.derive(
                basicMonthly,
                grossMonthly,
                statutory.profile(),
                statutory.epf(),
                statutory.esi(),
                statutory.dateOfBirth(),
                statutory.effectiveFrom());
    }

    /** Annual sum of the statutory lines included in CTC. */
    public static BigDecimal includedStatutoryAnnual(List<DerivedStatutoryLine> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (DerivedStatutoryLine line : lines) {
            if (line.includedInCtc()) {
                sum = sum.add(line.annualAmount().raw());
            }
        }
        return sum;
    }

    /** Monthly gross: the sum of every resolved earning's monthly amount. */
    public static BigDecimal grossMonthly(SplitOutput split) {
        return split.earnings().stream().map(CalculatedResult::monthlyAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal includedAnnual(SplitOutput split, BigDecimal additionalIncludedAnnual) {
        BigDecimal sum =
                (additionalIncludedAnnual != null ? additionalIncludedAnnual : BigDecimal.ZERO).setScale(4, ROUNDING);
        for (CalculatedResult e : split.earnings()) {
            if (e.includedInCtc()) {
                sum = sum.add(e.annualAmount());
            }
        }
        for (CalculatedResult b : split.benefits()) {
            if (b.includedInCtc()) {
                sum = sum.add(b.annualAmount());
            }
        }
        for (CalculatedResult r : split.reimbursements()) {
            if (r.includedInCtc()) {
                sum = sum.add(r.annualAmount());
            }
        }
        return sum;
    }

    private static BigDecimal includedMonthly(SplitOutput split) {
        BigDecimal sum = ZERO;
        for (List<CalculatedResult> list : List.of(split.earnings(), split.benefits(), split.reimbursements())) {
            for (CalculatedResult c : list) {
                if (c.includedInCtc()) {
                    sum = sum.add(c.monthlyAmount());
                }
            }
        }
        return sum;
    }

    private static boolean esiApplies(StatutoryInputs statutory) {
        return statutory != null
                && statutory.esi() != null
                && statutory.esi().isEnabled()
                && statutory.profile() != null
                && statutory.profile().isEligibleForEsi();
    }

    private static SalaryValidationException exceedsCtc(BigDecimal excessAnnual) {
        return new SalaryValidationException(
                "annualCtc",
                "Components and employer contributions exceed the CTC by " + excessAnnual.setScale(2, ROUNDING));
    }

    /**
     * Returns the index of the one balancing earning, or -1. Refuses a second one, a balancing benefit or
     * reimbursement, and an earning that is not FLAT, not in CTC, Basic, FBP or variable.
     */
    private static int findBalancingIndex(
            List<ComponentInput> earnings, List<ComponentInput> benefits, List<ComponentInput> reimbursements) {
        Map<String, String> errors = new LinkedHashMap<>();
        rejectBalancing("benefits", benefits, errors);
        rejectBalancing("reimbursements", reimbursements, errors);

        int found = -1;
        if (earnings != null) {
            for (int i = 0; i < earnings.size(); i++) {
                ComponentInput e = earnings.get(i);
                if (!e.balancing()) {
                    continue;
                }
                String field = "earnings[" + i + "].balancing";
                if (found >= 0) {
                    errors.put(field, "Only one earning can be the balancing earning");
                    continue;
                }
                found = i;
                String reason = balancingIneligibility(e);
                if (reason != null) {
                    errors.put(field, reason);
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new SalaryValidationException(errors);
        }
        return found;
    }

    private static void rejectBalancing(String category, List<ComponentInput> list, Map<String, String> errors) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).balancing()) {
                errors.put(category + "[" + i + "].balancing", "Only an earning can be the balancing component");
            }
        }
    }

    private static String balancingIneligibility(ComponentInput e) {
        CalculationType calcType = e.calculationType() != null ? e.calculationType() : CalculationType.FLAT;
        if (calcType != CalculationType.FLAT) {
            return "The balancing earning must be a flat amount";
        }
        if (!e.includedInCtc()) {
            return "The balancing earning must be included in CTC";
        }
        if (isBasic(e)) {
            return "Basic cannot be the balancing earning";
        }
        if (e.fbpComponent()) {
            return "A flexible benefit component cannot be the balancing earning";
        }
        if (e.variable()) {
            return "A variable earning cannot be the balancing earning";
        }
        return null;
    }

    private static List<ComponentInput> withBalancingValue(List<ComponentInput> earnings, int index, BigDecimal value) {
        List<ComponentInput> copy = new ArrayList<>(earnings);
        copy.set(index, earnings.get(index).withValue(value));
        return copy;
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

    public static CalculatedResult findBasicEarning(List<CalculatedResult> earnings) {
        for (CalculatedResult e : earnings) {
            if ("BASIC".equalsIgnoreCase(e.code()) || "BASIC".equalsIgnoreCase(e.earningType())) {
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
                input.carryForwardOption(),
                input.earningType());
    }
}
