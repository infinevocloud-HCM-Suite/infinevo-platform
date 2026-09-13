package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;

import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.StandardDeductionRuleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StandardDeductionRuleMasterRepository
        extends JpaRepository<StandardDeductionRuleMaster, Long> {

    Optional<StandardDeductionRuleMaster>
    findByFinancialYearAndTaxRegimeAndIsActiveTrue(
            String financialYear,
            String taxRegime
    );
}
