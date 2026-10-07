package com.infinevo.core.employee;

import java.util.Locale;
import java.util.Optional;

/**
 * The vocabulary of {@code core.employee.gender} (D-40).
 *
 * <p>The column stays {@code VARCHAR(32)} and {@link Employee#getGender()} stays a {@code String}:
 * {@code LeaveEligibilityServiceImpl} compares it as text and the payroll professional-tax lookup
 * passes it through as text, so changing its Java type would reach into both. What D-40 changes is
 * what may be written — {@link EmployeeServiceImpl} accepts only these four, stored as the name.
 *
 * <p>The frozen form offers {@code Male}, {@code Female} and {@code Other}
 * ({@code legacy/HRMS_Frontend/src/components/employee/AddEmployee.jsx:1015-1017}) with no way to
 * decline to answer; {@link #UNDISCLOSED} is that way. {@code V154__employee_profile_columns.sql}
 * normalises the rows written before this vocabulary existed.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER,
    UNDISCLOSED;

    /**
     * Reads a caller's spelling: the name in any case, or its single-letter abbreviation.
     *
     * <p>Lenient on purpose — "Female" is what the frozen form sends and "F" is what earlier callers of
     * this API sent, and neither is ambiguous. Anything else is empty, which the service reports as a
     * field error.
     */
    public static Optional<Gender> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String key = value.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "M", "MALE" -> Optional.of(MALE);
            case "F", "FEMALE" -> Optional.of(FEMALE);
            case "O", "OTHER" -> Optional.of(OTHER);
            case "U", "UNDISCLOSED" -> Optional.of(UNDISCLOSED);
            default -> Optional.empty();
        };
    }
}
