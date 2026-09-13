package com.itsdev.payroll.repository.employeeitdeclaration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.itsdev.payroll.entity.EmployeeITDeclaration.OldTaxCalculation;

import java.util.Optional;

@Repository
public interface OldTaxCalculationRepository
        extends JpaRepository<OldTaxCalculation, Long> {

    Optional<OldTaxCalculation> findByOrganizationIdAndEmployeeIdAndFinancialYear(
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
