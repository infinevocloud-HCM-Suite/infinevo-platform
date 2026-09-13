package com.itsdev.payroll.service.employeeTDS;

/**
 * Service for handling TDS recalculation scenarios
 * related to employee salary revisions.
 *
 * Responsibilities:
 * - Detect existing TDS history
 * - Recalculate tax using revised salary
 * - Insert new employee_tds entry with SALARY_REVISION source
 */
public interface TdsSalaryRevisionService {

    /**
     * Recalculate and update TDS after salary revision.
     *
     * @param organizationId organization identifier
     * @param employeeId     employee identifier
     * @param fiscalYear     fiscal year (FY end year, e.g. 2026 for FY 2025-26)
     */
    void handleSalaryRevisionTds(
            String organizationId,
            String employeeId,
            Integer fiscalYear
    );
}
