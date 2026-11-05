package com.infinevo.core.employee;

/**
 * {@code GET /api/v1/employees/limit} (W-84.2): the tenant's employee limit and its live usage, counted the way
 * the gate counts it — not soft-deleted, not {@code TERMINATED}.
 *
 * @param limit the plan's employee limit, {@code null} when there is none
 * @param used  employees in use
 */
public record EmployeeLimitResponse(Integer limit, long used) {

    static EmployeeLimitResponse from(EmployeeLimitGuard.Usage usage) {
        return new EmployeeLimitResponse(usage.limit(), usage.used());
    }
}
