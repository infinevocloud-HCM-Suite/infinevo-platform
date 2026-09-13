package com.itsdev.payroll.repository.claimsanddeclarations;

import com.itsdev.payroll.entity.claimsanddeclarations.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    // Check if reminder already sent for this employee, org, fiscal year and days
    // before
    @Query("SELECT COUNT(r) > 0 FROM Reminder r WHERE " +
            "r.employee.employeeId = :employeeId AND " +
            "r.organization.organizationId = :organizationId AND " +
            "r.fiscalYear = :fiscalYear AND " +
            "r.numberOfDays = :daysBefore")
    boolean existsByEmployeeAndOrganizationAndFiscalYearAndDaysBefore(
            @Param("employeeId") String employeeId,
            @Param("organizationId") String organizationId,
            @Param("fiscalYear") Integer fiscalYear,
            @Param("daysBefore") int daysBefore);

    // Find reminders by proof of investment
    List<Reminder> findByProofOfInvestmentId(Long proofOfInvestmentId);

    // Find by organization
    List<Reminder> findByOrganizationOrganizationId(String organizationId);
}