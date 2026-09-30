package com.infinevo.payroll.payrun;

import java.util.Locale;
import java.util.Optional;

/**
 * How often a variable earning is paid (W-29.2 §3). The column is free text on
 * {@code payroll.employee_earning.earning_frequency}; {@link #parse} reads it the way the legacy
 * {@code getPeriodicBonusAmount} did ({@code EmployeePayRunServiceImpl.java:449-520}) — by the word it
 * contains, case-insensitively — so "Half-Yearly", "HALF_YEARLY" and "half yearly" agree.
 */
public enum EarningFrequency {
    MONTHLY,
    QUARTERLY,
    HALF_YEARLY,
    YEARLY;

    /** The frequency the text names, or empty when it names none. "Half" is tested before "year". */
    public static Optional<EarningFrequency> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String t = text.trim().toLowerCase(Locale.ENGLISH);
        if (t.contains("half")) {
            return Optional.of(HALF_YEARLY);
        }
        if (t.contains("quart")) {
            return Optional.of(QUARTERLY);
        }
        if (t.contains("year") || t.contains("annual")) {
            return Optional.of(YEARLY);
        }
        if (t.contains("month")) {
            return Optional.of(MONTHLY);
        }
        return Optional.empty();
    }
}
