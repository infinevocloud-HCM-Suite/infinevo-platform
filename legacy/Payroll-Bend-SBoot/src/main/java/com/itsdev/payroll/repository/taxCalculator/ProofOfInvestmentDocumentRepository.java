package com.itsdev.payroll.repository.taxCalculator;

import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.taxCalculator.ProofOfInvestmentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProofOfInvestmentDocumentRepository extends JpaRepository<ProofOfInvestmentDocument, Long> {

    // Employee queries
    List<ProofOfInvestmentDocument> findByEmployeeAndOrganizationIdAndFinancialYear(
            BasicDetails employee, String organizationId, Integer financialYear);

    // Admin queries
    List<ProofOfInvestmentDocument> findByOrganizationIdAndFinancialYear(String organizationId, Integer financialYear);

    List<ProofOfInvestmentDocument> findByOrganizationIdAndFinancialYearAndStatus(
            String organizationId, Integer financialYear, ProofOfInvestmentDocument.DocumentStatus status);

    // Utility queries
    boolean existsByEmployeeAndDeclaredItemNameAndFinancialYear(
            BasicDetails employee, String declaredItemName, Integer financialYear);
}