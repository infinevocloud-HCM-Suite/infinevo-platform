package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;

import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.LetOutPropertyRuleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LetOutPropertyRuleMasterRepository
        extends JpaRepository<LetOutPropertyRuleMaster, Long> {

    Optional<LetOutPropertyRuleMaster> findByIsActiveTrue();

    Optional<LetOutPropertyRuleMaster> findByIsActiveTrueAndTaxRegime(String taxRegime);
}

