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

    /**
     * D-145: what the next regular pay run deducts for the record, as the officer's card shows it. The pay
     * month is the month that run will cover: the first month of the financial year, not before the record's
     * effective month, with no regular run computed, approved or paid (a draft or locked run, or none, leaves
     * the month to be paid). {@code deductedSoFar} counts every computed, approved and paid run, so the share
     * is the remaining tax over the months from that month to March. When every month to March is counted,
     * months left and the share are zero and there is no next pay month. The arithmetic is {@link TdsSpread},
     * which the pay run line uses.
     */
    TdsSpread spread(EmployeeTds record, BigDecimal deductedSoFar);
}
