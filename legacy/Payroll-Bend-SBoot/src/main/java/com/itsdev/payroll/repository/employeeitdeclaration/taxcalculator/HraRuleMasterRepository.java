package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;



import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.HraRuleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HraRuleMasterRepository
        extends JpaRepository<HraRuleMaster, Long> {

    Optional<HraRuleMaster> findByIsActiveTrue();
}

