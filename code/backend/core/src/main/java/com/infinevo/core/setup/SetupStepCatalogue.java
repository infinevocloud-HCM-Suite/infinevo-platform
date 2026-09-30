package com.infinevo.core.setup;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogue of setup steps (W-24.1, spec §4).
 *
 * <p>Code, not rows — a step exists because a feature exists, and a database table
 * would let the two disagree. Holds ordering and display metadata; actual completion
 * logic is supplied by {@link SetupStepChecker} implementations.
 *
 * <p><strong>Prior payroll and organisation tax are not listed yet.</strong> The frozen model
 * carried both as flags ({@code OrgSetupSteps.java:15-23}), but neither feature exists on the
 * new platform: prior payroll import is {@code W-38} and has no table
 * ({@code 02-data-model.md} {@code PAY-17}), and the organisation's tax deductor details are
 * {@code W-36.3} ({@code payroll.tax_deductor}, not yet migrated). A step whose checker has
 * nothing to look at could never complete, so each returns with its owning ticket. Display
 * orders 4 and 5 are kept free for them; adding them later does not drop an existing tenant's
 * progress (spec §13 decision 1, {@link SetupChecklistService}).
 */
public final class SetupStepCatalogue {

    public record StepDefinition(String code, String label, PlatformModule module, int displayOrder) {}

    public static final List<StepDefinition> DEFAULT_STEPS = List.of(
            new StepDefinition("WORK_LOCATION", "Work location", null, 1),
            new StepDefinition("EMPLOYEE", "Employee", null, 2),
            new StepDefinition("PAY_SCHEDULE", "Pay schedule", PlatformModule.PAYROLL, 3),
            new StepDefinition("SALARY_COMPONENTS", "Salary components", PlatformModule.PAYROLL, 6),
            new StepDefinition("EPF", "EPF", PlatformModule.PAYROLL, 7),
            new StepDefinition("ESI", "ESI", PlatformModule.PAYROLL, 8),
            new StepDefinition("PROFESSIONAL_TAX", "Professional tax", PlatformModule.PAYROLL, 9));

    private SetupStepCatalogue() {}

    public static Optional<StepDefinition> findByCode(String code) {
        return findByCode(DEFAULT_STEPS, code);
    }

    static Optional<StepDefinition> findByCode(List<StepDefinition> catalogue, String code) {
        if (code == null) {
            return Optional.empty();
        }
        return catalogue.stream().filter(s -> s.code().equalsIgnoreCase(code)).findFirst();
    }

    /**
     * Checks a checker registry against the catalogue and returns every problem found (spec §4:
     * a catalogue step with no registered checker is a build error, not a failure on first read).
     *
     * <p>Problems: two checkers for one code; a checker whose {@code module()} differs from its
     * catalogue step; a catalogue step with no checker. With {@code requireEveryModule} false, a
     * step is only required when its module is core ({@code null}) or the module registered at
     * least one checker — a context that does not deploy a module at all (the core-only test
     * slices) is not a context that forgot one of its steps. With it true, every step is required;
     * that is the full application, asserted by {@code PayrollSetupStepConfigurationTest}.
     */
    public static List<String> registryProblems(
            List<StepDefinition> catalogue, Collection<SetupStepChecker> checkers, boolean requireEveryModule) {
        List<String> problems = new ArrayList<>();
        Map<String, SetupStepChecker> byCode = new HashMap<>();
        Set<PlatformModule> modulesWithCheckers = new HashSet<>();
        for (SetupStepChecker checker : checkers) {
            String code = checker.code() == null ? null : checker.code().toUpperCase();
            if (code == null || code.isBlank()) {
                problems.add("SetupStepChecker " + checker.getClass().getName() + " has no code");
                continue;
            }
            if (byCode.putIfAbsent(code, checker) != null) {
                problems.add("More than one SetupStepChecker registered for step: " + code);
            }
            if (checker.module() != null) {
                modulesWithCheckers.add(checker.module());
            }
        }
        for (StepDefinition def : catalogue) {
            SetupStepChecker checker = byCode.get(def.code().toUpperCase());
            boolean required = requireEveryModule || def.module() == null || modulesWithCheckers.contains(def.module());
            if (checker == null) {
                if (required) {
                    problems.add("No SetupStepChecker registered for step: " + def.code());
                }
            } else if (checker.module() != def.module()) {
                problems.add("SetupStepChecker for step " + def.code() + " declares module " + checker.module()
                        + " but the catalogue says " + def.module());
            }
        }
        return problems;
    }
}
