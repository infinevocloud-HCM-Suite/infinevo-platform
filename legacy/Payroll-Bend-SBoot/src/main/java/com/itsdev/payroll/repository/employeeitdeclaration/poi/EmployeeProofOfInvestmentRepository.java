package com.itsdev.payroll.repository.employeeitdeclaration.poi;

import com.itsdev.payroll.entity.EmployeeITDeclaration.poi.EmployeeProofOfInvestment;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface EmployeeProofOfInvestmentRepository extends JpaRepository<EmployeeProofOfInvestment, Long> {

    Optional<EmployeeProofOfInvestment> findByOrganization_IdAndEmployee_IdAndFiscalYear(
            Long organizationId,
            Long employeeId,
            Integer fiscalYear);

    boolean existsByOrganization_IdAndEmployee_IdAndFiscalYear(
            Long organizationId,
            Long employeeId,
            Integer fiscalYear);

    Optional<EmployeeProofOfInvestment> findByOrganizationAndEmployeeAndFiscalYear(
            Organization organization,
            BasicDetails employee,
            Integer fiscalYear);

    /**
     * Used ONLY for POI reminder evaluation.
     * Fetches all POIs that are NOT submitted for a given org & fiscal year.
     * FIXED: Use enum comparison correctly
     */
    @Query("""
            SELECT p
            FROM EmployeeProofOfInvestment p
            WHERE p.organization.organizationId = :orgId
              AND p.fiscalYear = :fiscalYear
              AND p.status = com.itsdev.payroll.enumeration.payruns.PayRunStatus.DRAFT
            """)
    List<EmployeeProofOfInvestment> findPendingPOIForReminder(
            @Param("orgId") String orgId,
            @Param("fiscalYear") Integer fiscalYear);

    // Additional query to find by organization ID (string)
    @Query("SELECT p FROM EmployeeProofOfInvestment p WHERE p.organization.organizationId = :orgId")
    List<EmployeeProofOfInvestment> findByOrganizationId(@Param("orgId") String orgId);

    // Find by employee and fiscal year
    @Query("SELECT p FROM EmployeeProofOfInvestment p WHERE p.employee.employeeId = :employeeId AND p.fiscalYear = :fiscalYear")
    List<EmployeeProofOfInvestment> findByEmployeeIdAndFiscalYear(
            @Param("employeeId") String employeeId,
            @Param("fiscalYear") Integer fiscalYear);

    // Alternative: Use named parameter for enum
    @Query("SELECT p FROM EmployeeProofOfInvestment p WHERE p.organization.organizationId = :orgId AND p.fiscalYear = :fiscalYear AND p.status = :status")
    List<EmployeeProofOfInvestment> findByOrganizationIdAndFiscalYearAndStatus(
            @Param("orgId") String orgId,
            @Param("fiscalYear") Integer fiscalYear,
            @Param("status") PayRunStatus status);

    /**
     * Find all POIs for admin listing with employee details
     */
    // Update the native query to filter by POI's tax_regime_at_submission:
    @Query(value = """
            SELECT p.*
            FROM employee_proof_of_investment p
            INNER JOIN employee e ON p.employee_id = e.employee_id
            INNER JOIN organization o ON p.organization_id = o.id
            WHERE o.organizationId = :organizationId
              AND (:fiscalYear IS NULL OR p.fiscal_year = :fiscalYear)
              AND (:status IS NULL OR :status = 'all' OR p.status = :status)
              AND (:taxRegime IS NULL OR :taxRegime = 'all' OR p.tax_regime_at_submission = :taxRegime)
              AND e.is_deleted = false
              AND p.status != 'DRAFT'  -- EXCLUDE DRAFT from admin view
            ORDER BY p.created_time DESC
            """, countQuery = """
            SELECT COUNT(*)
            FROM employee_proof_of_investment p
            INNER JOIN employee e ON p.employee_id = e.employee_id
            INNER JOIN organization o ON p.organization_id = o.id
            WHERE o.organizationId = :organizationId
              AND (:fiscalYear IS NULL OR p.fiscal_year = :fiscalYear)
              AND (:status IS NULL OR :status = 'all' OR p.status = :status)
              AND (:taxRegime IS NULL OR :taxRegime = 'all' OR p.tax_regime_at_submission = :taxRegime)
              AND e.is_deleted = false
              AND p.status != 'DRAFT'  -- EXCLUDE DRAFT from admin view
            """, nativeQuery = true)
    Page<EmployeeProofOfInvestment> findAllForAdminListing(
            @Param("organizationId") String organizationId,
            @Param("fiscalYear") Integer fiscalYear,
            @Param("status") String status,
            @Param("taxRegime") String taxRegime, // "with_exemptions", "without_exemptions", or "all"
            Pageable pageable);



    Optional<EmployeeProofOfInvestment>
    findByOrganization_IdAndEmployee_IdAndFiscalYearAndStatus(
            Long organizationId,
            Long employeeId,
            Integer fiscalYear,
            PayRunStatus status
    );



    boolean existsByOrganization_OrganizationIdAndEmployee_EmployeeIdAndFiscalYearAndStatus(
            String organizationId,
            String employeeId,
            Integer fiscalYear,
            PayRunStatus status
    );

}