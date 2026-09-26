package com.infinevo.core.setup;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.List;
import java.util.Optional;

/**
 * Catalogue of setup steps (W-24.1, spec §4).
 *
 * <p>Code, not rows — a step exists because a feature exists, and a database table
 * would let the two disagree. Holds ordering and display metadata; actual completion
 * logic is supplied by {@link SetupStepChecker} implementations.
 */
public final class SetupStepCatalogue {

    public record StepDefinition(String code, String label, PlatformModule module, int displayOrder) {}

    public static final List<StepDefinition> DEFAULT_STEPS = List.of(
            new StepDefinition("WORK_LOCATION", "Work location", null, 1),
            new StepDefinition("EMPLOYEE", "Employee", null, 2),
            new StepDefinition("PAY_SCHEDULE", "Pay schedule", PlatformModule.PAYROLL, 3),
            new StepDefinition("PRIOR_PAYROLL", "Prior payroll", PlatformModule.PAYROLL, 4),
            new StepDefinition("ORGANISATION_TAX", "Organisation tax", PlatformModule.PAYROLL, 5),
            new StepDefinition("SALARY_COMPONENTS", "Salary components", PlatformModule.PAYROLL, 6),
            new StepDefinition("EPF", "EPF", PlatformModule.PAYROLL, 7),
            new StepDefinition("ESI", "ESI", PlatformModule.PAYROLL, 8),
            new StepDefinition("PROFESSIONAL_TAX", "Professional tax", PlatformModule.PAYROLL, 9));

    private SetupStepCatalogue() {}

    public static Optional<StepDefinition> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return DEFAULT_STEPS.stream()
                .filter(s -> s.code().equalsIgnoreCase(code))
                .findFirst();
    }
}
