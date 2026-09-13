package com.itsdev.payroll.repository.employeeitdeclaration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.itsdev.payroll.entity.EmployeeITDeclaration.NewTaxCalculation;

import java.util.Optional;

@Repository
public interface NewTaxCalculationRepository
        extends JpaRepository<NewTaxCalculation, Long> {

    Optional<NewTaxCalculation> findByOrganizationIdAndEmployeeIdAndFinancialYear(
            String organizationId,
            String employeeId,
            Integer financialYear
    );

    boolean existsByOrganizationIdAndEmployeeIdAndFinancialYear(
            String organizationId,
            String employeeId,
            Integer financialYear
    );

    void deleteByOrganizationIdAndEmployeeIdAndFinancialYear(
            String organizationId,
            String employeeId,
            Integer financialYear
    );
}

