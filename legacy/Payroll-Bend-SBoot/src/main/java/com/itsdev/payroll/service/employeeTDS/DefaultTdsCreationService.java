package com.itsdev.payroll.service.employeeTDS;

import com.itsdev.payroll.entity.employeeTDS.EmployeeTds;

public interface DefaultTdsCreationService {

    /**
     * Creates default EmployeeTds record if no active TDS exists
     * for the employee in the given fiscal year.
     *
     * Production rules:
     * - Must NOT be called silently inside PayRun
     * - Must store full financial snapshot
     * - Must respect org default tax regime
     *
     * @param organizationId Organization identifier
     * @param employeeId     Employee identifier
     * @param fiscalYear     Financial year (e.g., 2026 for FY 2025-26)
     * @return Active EmployeeTds record (existing or newly created)
     */
    EmployeeTds createDefaultTdsIfNotExists(
            String organizationId,
            String employeeId,
            Integer fiscalYear
    );
}
