package com.infinevo.core.employee.detail;

import java.time.LocalDate;
import java.util.Map;

/**
 * The D-40 rules on an employee's terms of employment, held once.
 *
 * <p>Two endpoints write these fields — {@code POST /api/v1/employees} on create
 * ({@code EmployeeServiceImpl}) and {@code PUT /api/v1/employees/{id}/employment}
 * ({@link EmployeeEmploymentServiceImpl}) — and two definitions of "a valid notice period" is how a
 * value accepted by one is refused by the other. The bounds match the {@code CHECK} constraints in
 * {@code V154__employee_profile_columns.sql}.
 */
public final class EmploymentTerms {

    /** Inclusive bounds on {@code notice_period_days}. */
    public static final int MIN_NOTICE_PERIOD_DAYS = 0;

    public static final int MAX_NOTICE_PERIOD_DAYS = 365;

    private EmploymentTerms() {}

    /**
     * Adds a field error for each rule broken. Throws nothing.
     *
     * @param dateOfJoining the joining date the probation is measured from, or null when there is none
     *     to compare against — the date rule is then skipped, not failed
     */
    public static void check(
            Map<String, String> errors, LocalDate dateOfJoining, LocalDate probationEndDate, Integer noticePeriodDays) {
        if (noticePeriodDays != null
                && (noticePeriodDays < MIN_NOTICE_PERIOD_DAYS || noticePeriodDays > MAX_NOTICE_PERIOD_DAYS)) {
            errors.put(
                    "noticePeriodDays",
                    "noticePeriodDays must be between " + MIN_NOTICE_PERIOD_DAYS + " and " + MAX_NOTICE_PERIOD_DAYS);
        }
        // Equal is allowed: a probation that ends on the joining date is no probation, which is a
        // legitimate thing to record. Before it is a typo.
        if (probationEndDate != null && dateOfJoining != null && probationEndDate.isBefore(dateOfJoining)) {
            errors.put("probationEndDate", "probationEndDate cannot precede dateOfJoining");
        }
    }
}
