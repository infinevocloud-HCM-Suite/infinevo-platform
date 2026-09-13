package com.itsdev.payroll.repository.employeeitdeclaration.taxcalculator;

import com.itsdev.payroll.entity.EmployeeITDeclaration.taxCalculator.TaxSlabMasterHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxSlabMasterHistoryRepository
        extends JpaRepository<TaxSlabMasterHistory, Long> {

    /** Audit log for one slab master, newest first. */
    List<TaxSlabMasterHistory> findByTaxSlabMasterIdOrderByChangedAtDesc(Long taxSlabMasterId);
}
