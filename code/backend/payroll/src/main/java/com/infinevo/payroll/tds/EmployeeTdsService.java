package com.infinevo.payroll.tds;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing employee TDS annual records (W-36.1 §4).
 *
 * <p>All methods operate on the bound tenant from {@code TenantContext}.
 */
public interface EmployeeTdsService {

    /**
     * Supersedes any currently active row for the employee and financial year, and inserts a new active row.
     * Called by the officer PUT endpoint and by the tax calculator (W-33).
     */
    EmployeeTds record(UUID employeeId, String financialYear, TdsFigures figures);

    /**
     * Finds the currently active TDS record for the given employee and financial year in the bound tenant.
     */
    Optional<EmployeeTds> active(UUID employeeId, String financialYear);

    /**
     * Returns all TDS records (active and superseded) for the employee and financial year, newest first.
     */
    List<EmployeeTds> history(UUID employeeId, String financialYear);

    /**
     * Sums all TAX deduction lines on this tenant's runs whose period is in the financial year
     * and whose status is COMPUTED, APPROVED or PAID.
     */
    BigDecimal yearToDate(UUID employeeId, String financialYear);
}
