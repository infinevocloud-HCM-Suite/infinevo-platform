package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;



import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.revision.OldTaxCalculationRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OldTaxCalculationRevisionRepository
        extends JpaRepository<OldTaxCalculationRevision, Long> {

    Optional<OldTaxCalculationRevision> findByOrganizationIdAndEmployeeIdAndFinancialYear(
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
