package com.itsdev.payroll.repository.employee;

import com.itsdev.payroll.entity.SalaryDeduction;
import com.itsdev.payroll.enumeration.DeductionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SalaryDeductionRepository extends JpaRepository<SalaryDeduction, Long>, JpaSpecificationExecutor<SalaryDeduction> {

    List<SalaryDeduction> findByOrganizationIdAndEmployeeIdOrderByDeductionMonthDesc(String organizationId, String employeeId);

    /**
     * Find all deductions for a specific employee in an organization
     * for a given month with the specified status.
     * Used during payrun creation to fetch ACTIVE deductions for the processing period.
     */
    List<SalaryDeduction> findByEmployeeIdAndOrganizationIdAndDeductionMonthAndStatus(
            String employeeId, String organizationId, LocalDate deductionMonth, DeductionStatus status);

    /**
     * Find all deductions in a specific organization for a given month with the specified status.
     * Used during payrun approve/reject/delete to bulk update statuses.
     */
    List<SalaryDeduction> findByOrganizationIdAndDeductionMonthAndStatus(
            String organizationId, LocalDate deductionMonth, DeductionStatus status);
}
