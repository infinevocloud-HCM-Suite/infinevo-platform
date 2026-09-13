package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;

import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.Section87ARebateRuleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface Section87aRebateRuleMasterRepository
        extends JpaRepository<Section87ARebateRuleMaster, Long> {

    Optional<Section87ARebateRuleMaster>
    findByTaxRegimeAndIsActiveTrue(String taxRegime);
}

