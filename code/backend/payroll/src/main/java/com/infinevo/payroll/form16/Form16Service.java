package com.infinevo.payroll.form16;

import java.util.UUID;

/**
 * Service for rendering annual tax statement Form 16 Part B (W-36.4).
 */
public interface Form16Service {

    /**
     * Renders the Form 16 statement for a specific employee in the bound tenant.
     *
     * @param employeeId the employee ID
     * @param financialYear the financial year (e.g. "2026-2027")
     * @return the Form 16 statement
     */
    Form16Statement render(UUID employeeId, String financialYear);

    /**
     * Renders the Form 16 statement for the currently authenticated employee.
     *
     * @param financialYear the financial year
     * @return the Form 16 statement
     */
    Form16Statement renderOwn(String financialYear);
}
