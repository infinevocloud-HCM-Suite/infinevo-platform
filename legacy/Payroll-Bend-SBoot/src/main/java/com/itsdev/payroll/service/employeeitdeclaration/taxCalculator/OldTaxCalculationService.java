package com.itsdev.payroll.service.employeeitdeclaration.taxCalculator;

import com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator.OldTaxCalculationResult;

public interface OldTaxCalculationService {

    OldTaxCalculationResult calculateOldTax(
            String organizationId,
            String employeeId,
            Integer financialYear
    );

    OldTaxCalculationResult calculateAndSaveOldTax(
            String organizationId,
            String employeeId,
            Integer financialYear
    );
     
     OldTaxCalculationResult calculateOldRegimeTaxUsingPOI(
             String organizationId,
             String employeeId,
             Integer financialYear
     );


    OldTaxCalculationResult calculateOldTaxWithRevisedSalary(
            String organizationId,
            String employeeId,
            Integer financialYear
    );

    OldTaxCalculationResult calculateOldTaxWithRevisedSalaryAndApprovedPOI(
            String organizationId,
            String employeeId,
            Integer financialYear
    );

}

