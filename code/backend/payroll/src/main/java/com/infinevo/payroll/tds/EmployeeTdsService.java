package com.infinevo.payroll.tds;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing annual TDS records and year-to-date tax deductions (W-36.1 §4).
 */
public interface EmployeeTdsService {

    /**
     * Records annual TDS figures, superseding any currently active record for the financial year.
     */
    EmployeeTdsResponse record(UUID employeeId, String financialYear, TdsFigures figures);

    /**
     * Records annual TDS figures with an explicit source, superseding any currently active record.
     */
    EmployeeTdsResponse record(UUID employeeId, String financialYear, TdsFigures figures, TdsSource source);

    /**
     * Returns the currently active TDS record for the employee and financial year with YTD and remaining.
     */
    Optional<EmployeeTdsResponse> active(UUID employeeId, String financialYear);

    /**
     * Returns the currently active TDS entity for the tenant, employee and financial year.
     */
    Optional<EmployeeTds> activeEntity(UUID tenantId, UUID employeeId, String financialYear);

    /**
     * Returns all TDS records for the employee and financial year, newest first.
     */
    List<EmployeeTdsResponse> history(UUID employeeId, String financialYear);

    /**
     * Sum of tax lines deducted across COMPUTED, APPROVED or PAID runs in the financial year.
     */
    BigDecimal yearToDate(UUID employeeId, String financialYear);

    /**
     * Returns own active TDS record under /me context with YTD and remaining.
     */
    Optional<EmployeeTdsResponse> activeOwn(String financialYear);
}
