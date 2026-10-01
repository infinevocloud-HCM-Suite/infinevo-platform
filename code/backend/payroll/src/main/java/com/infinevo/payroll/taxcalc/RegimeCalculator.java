package com.infinevo.payroll.taxcalc;

import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxcalc.model.TaxInput;
import com.infinevo.payroll.taxdeclaration.FinancialYear;

/**
 * Strategy contract for computing income tax under a specific regime (W-33.1 spec ? 2, ? 4).
 *
 * <p>Each regime implements this interface as a Spring bean. W-33.1 ships {@code NewRegimeCalculator};
 * W-33.2 plugs {@code OldRegimeCalculator} into this exact seam.
 */
public interface RegimeCalculator {

    /**
     * The tax regime this calculator supports (e.g. {@link TaxRegime#NEW}).
     */
    TaxRegime regime();

    /**
     * Pure calculation of income tax for an employee across a financial year.
     *
     * @param input aggregated employee tax inputs
     * @param fy the financial year
     * @return {@link TaxComputation} result with every intermediate figure
     */
    TaxComputation compute(TaxInput input, FinancialYear fy);
}
