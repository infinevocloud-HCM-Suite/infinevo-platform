package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;


import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.HomeLoanRuleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HomeLoanRuleMasterRepository
        extends JpaRepository<HomeLoanRuleMaster, Long> {

    List<HomeLoanRuleMaster> findByIsActiveTrue();

}

