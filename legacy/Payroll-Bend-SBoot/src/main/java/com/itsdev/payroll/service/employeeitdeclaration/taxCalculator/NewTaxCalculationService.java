package com.itsdev.payroll.service.employeeitdeclaration.taxCalculator;

import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.NewTaxCalculationResult;

public interface NewTaxCalculationService {

    /**
     * Standard NEW regime tax calculation
     * (uses latest applicable CTC only)
     */
    NewTaxCalculationResult calculateNewTax(
            String organizationId,
            String employeeId,
            Integer financialYear
    );

    /**
     * NEW regime tax calculation and persistence
     */
    Long calculateAndSaveNewTax(
            String organizationId,
            String employeeId,
            Integer financialYear
    );

    /**
     * NEW regime tax calculation after POI approval
     * (POI does NOT affect NEW regime calculation)
     */
    NewTaxCalculationResult calculateNewTaxWithPOI(
            String organizationId,
            String employeeId,
            Integer financialYear
    );

    /**
     * ✅ NEW METHOD
     * NEW regime tax calculation using revised salary
     * (CTC effective_date based, full FY recalculation)
     */
    NewTaxCalculationResult calculateNewTaxWithRevisedSalary(
            String organizationId,
            String employeeId,
            Integer financialYear
    );
}
