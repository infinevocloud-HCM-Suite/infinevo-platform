package com.infinevo.core.ai.help;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where each setup step is finished (W-114.16): the menu key that opens it, its path and, when one exists, the
 * W-114.11 article. The same pairs as the frontend's {@code core/setup/stepLinks.js:7-27}; the backend step itself
 * holds no path ({@code setup/SetupStepResponse.java}).
 *
 * <p>Four paths sit one level below {@code /payroll/settings} and are on {@code help/sub-routes.json}, so W-114.13
 * links them only for a caller whose menu holds that parent. {@code SetupStepScreensTest} fails when a step of
 * {@code SetupStepCatalogue.DEFAULT_STEPS} has no entry here.
 */
public final class SetupStepScreens {

    /** One step's screen: the menu item that opens it, the path, and the help article id or {@code null}. */
    public record Screen(String menuKey, String path, String article) {}

    private static final Map<String, Screen> BY_STEP = new LinkedHashMap<>();

    static {
        BY_STEP.put("WORK_LOCATION", new Screen("core.org.locations", "/org/work-locations", "work-locations"));
        BY_STEP.put("EMPLOYEE", new Screen("core.employee", "/employees", "add-employee"));
        BY_STEP.put("PAY_SCHEDULE", new Screen("payroll.settings", "/payroll/settings/pay-schedule", null));
        BY_STEP.put("PRIOR_PAYROLL", new Screen("payroll.prior_payroll", "/payroll/prior-payroll", null));
        BY_STEP.put("SALARY_COMPONENTS", new Screen("payroll.components", "/payroll/components", "salary-components"));
        BY_STEP.put("EPF", new Screen("payroll.settings", "/payroll/settings/epf", "statutory-settings"));
        BY_STEP.put("ESI", new Screen("payroll.settings", "/payroll/settings/esi", "statutory-settings"));
        BY_STEP.put(
                "PROFESSIONAL_TAX",
                new Screen("payroll.settings", "/payroll/settings/professional-tax", "statutory-settings"));
    }

    private SetupStepScreens() {}

    /** The screen of a step code, or empty for a code with no entry. */
    public static Optional<Screen> of(String stepCode) {
        return Optional.ofNullable(stepCode == null ? null : BY_STEP.get(stepCode));
    }

    /** Every entry, step code to screen, in checklist order. */
    public static Map<String, Screen> all() {
        return Map.copyOf(BY_STEP);
    }
}
