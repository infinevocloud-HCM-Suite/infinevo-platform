package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;



import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.TaxSlabMaster;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxSlabMasterRepository
        extends JpaRepository<TaxSlabMaster, Long> {

    List<TaxSlabMaster> findByTaxRegimeAndIsActiveTrue(
            String taxRegime
    );

    // All active regimes (used by the "TaxSlab Regim" tab to show OLD + NEW cards)
    List<TaxSlabMaster> findByIsActiveTrueOrderByTaxRegimeAsc();

        List<TaxSlabMaster>
    findByFinancialYearAndTaxRegimeAndIsActiveTrue(
            String financialYear,
            String taxRegime
    );

}

