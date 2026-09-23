package com.infinevo.core.employee;

import java.util.Set;

/**
 * Where an employee stands with the organisation (W-13.1).
 *
 * <p>Stored as the string name in {@code core.employee.status} —
 * {@code migration/src/main/resources/db/migration/core/V010__employee.sql:14} — not as an ordinal.
 * An ordinal survives a reordering of this file silently and turns every existing row into a
 * different status.
 *
 * <p>The frozen system keeps the same idea as a free-text column, {@code employee_status} —
 * {@code legacy/Payroll-Bend-SBoot/src/main/java/com/itsdev/payroll/entity/employee/BasicDetails.java:72}
 * — with no constraint and no vocabulary, so each screen invents its own spelling. Three named
 * states replace it.
 *
 * <p>Termination is final. An employee who left and returned is a new employment, with its own
 * joining date and its own row; reviving the old row would leave one record claiming two
 * joining dates and break every date-bounded payroll and leave calculation over it.
 */
public enum EmploymentStatus {

    /** On the books and working. */
    ACTIVE,

    /** On the books, not working, and expected back — a suspension or an unpaid break. */
    SUSPENDED,

    /** Off the books. Terminal: {@code termination_date} is set and no transition leaves it. */
    TERMINATED;

    /** True when {@code target} is a legal next state from this one. */
    public boolean canTransitionTo(EmploymentStatus target) {
        return allowedNext().contains(target);
    }

    /** The states reachable from this one, itself included — a no-op update is not a transition. */
    public Set<EmploymentStatus> allowedNext() {
        return switch (this) {
            case ACTIVE -> Set.of(ACTIVE, SUSPENDED, TERMINATED);
            case SUSPENDED -> Set.of(SUSPENDED, ACTIVE, TERMINATED);
            case TERMINATED -> Set.of(TERMINATED);
        };
    }

    /** True when this status requires {@code termination_date} to be set, and only then. */
    public boolean requiresTerminationDate() {
        return this == TERMINATED;
    }
}
