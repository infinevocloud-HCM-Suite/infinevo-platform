package com.infinevo.payroll.taxcalc;

import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing employee tax computation engine runs and summary persistence (W-33.1).
 */
public interface TaxCalculationService {

    /**
     * Previews tax computation for the specified employee, financial year, and regime.
     * Pure operation ? does not persist to database.
     *
     * @param employeeId the employee UUID
     * @param fy the financial year
     * @param regime the tax regime (if null, defaults to declaration header's regime)
     * @return the computation result breakdown
     */
    TaxComputation compute(UUID employeeId, FinancialYear fy, TaxRegime regime);

    /**
     * Computes tax for every regime known to the engine, records each result in the tax summary
     * row via {@code TaxSummaryService.record}, and returns the map of computations.
     *
     * @param employeeId the employee UUID
     * @param fy the financial year
     * @return map of computations for each available regime (e.g. NEW, and OLD when available)
     */
    Map<TaxRegime, TaxComputation> computeAndRecord(UUID employeeId, FinancialYear fy);
}
