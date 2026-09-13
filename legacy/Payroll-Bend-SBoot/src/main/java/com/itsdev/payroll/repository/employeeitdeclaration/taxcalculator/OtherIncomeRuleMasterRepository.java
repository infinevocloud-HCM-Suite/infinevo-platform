package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;


import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.OtherIncomeRuleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtherIncomeRuleMasterRepository
        extends JpaRepository<OtherIncomeRuleMaster, Long> {

    List<OtherIncomeRuleMaster> findByIsActiveTrue();
}

