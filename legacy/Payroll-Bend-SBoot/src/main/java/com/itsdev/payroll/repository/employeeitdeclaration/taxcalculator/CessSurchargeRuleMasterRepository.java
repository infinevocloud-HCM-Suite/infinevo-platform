package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;

import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.CessSurchargeRuleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CessSurchargeRuleMasterRepository
        extends JpaRepository<CessSurchargeRuleMaster, Long> {

    List<CessSurchargeRuleMaster> findByIsActiveTrue();
}

