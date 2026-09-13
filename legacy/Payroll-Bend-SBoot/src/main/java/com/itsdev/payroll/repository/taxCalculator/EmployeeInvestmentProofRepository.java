package com.itsdev.payroll.repository.taxCalculator;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.itsdev.payroll.entity.taxCalculator.EmployeeInvestmentProof;

public interface EmployeeInvestmentProofRepository extends JpaRepository<EmployeeInvestmentProof, Long> {
	
	Optional<EmployeeInvestmentProof> findByOrganizationIdAndEmployee_EmployeeIdAndFinancialYear(
	        String organizationId, String employeeId, Integer financialYear);

	
}
