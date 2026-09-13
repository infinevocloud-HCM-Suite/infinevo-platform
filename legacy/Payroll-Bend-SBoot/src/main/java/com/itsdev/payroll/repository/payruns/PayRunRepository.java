package com.itsdev.payroll.repository.payruns;

import com.itsdev.payroll.entity.organization.Organization;
import com.itsdev.payroll.entity.payruns.PayRun;
import com.itsdev.payroll.enumeration.payruns.PayRunStatus;
import com.itsdev.payroll.enumeration.payruns.PayRunType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayRunRepository extends JpaRepository<PayRun, Long> {

        List<PayRun> findByOrganizationAndStatusNot(Organization organization, PayRunStatus status);

        List<PayRun> findByOrganization(Organization organization);

        boolean existsByOrganization(Organization organization);

        Optional<PayRun> findByPayrunIdAndOrganization(String payrunId, Organization organization);

        boolean existsByPayrunId(String payrunId);

        long countByOrganizationAndTypeAndStatus(Organization organization, PayRunType type, PayRunStatus status);

        Optional<PayRun> findTopByOrganizationAndTypeOrderByPayPeriodStartDateDesc(Organization organization,
                        PayRunType type);

        Optional<PayRun> findFirstByOrganizationAndTypeAndStatusOrderByPayPeriodEndDateDesc(
                        Organization organization, PayRunType type, PayRunStatus status);

        boolean existsByOrganizationAndPayPeriodStartDateAndPayPeriodEndDate(
                        Organization organization, LocalDate payPeriodStartDate, LocalDate payPeriodEndDate);

        List<PayRun> findByOrganizationAndStatus(Organization organization, PayRunStatus status);

        boolean existsByOrganizationAndTypeAndPayPeriodStartDateAndPayPeriodEndDate(
                        Organization organization, PayRunType type, LocalDate payPeriodStartDate,
                        LocalDate payPeriodEndDate);

        Optional<PayRun> findByOrganizationAndPayrunId(Organization organization, String payrunId);

        boolean existsByOrganization_OrganizationId(String organizationId);

        @Query("""
                            SELECT COUNT(pr)
                            FROM PayRun pr
                            JOIN pr.employeePayRuns epr
                            WHERE pr.organization.organizationId = :orgId
                              AND epr.employee.employeeId = :employeeId
                              AND pr.type = com.itsdev.payroll.enumeration.payruns.PayRunType.REGULAR
                              AND pr.status <> com.itsdev.payroll.enumeration.payruns.PayRunStatus.COMPLETED
                        """)
        long countActiveRegularPayRuns(String orgId, String employeeId);

        List<PayRun> findByOrganizationAndType(Organization organization, PayRunType type);

        // Add to PayRunRepository.java

        // For date range filtering
        @Query("SELECT pr FROM PayRun pr WHERE pr.organization.organizationId = :organizationId " +
                        "AND pr.status = :status AND pr.payDate BETWEEN :fromDate AND :toDate " +
                        "ORDER BY pr.payDate DESC")
        List<PayRun> findByOrganizationAndStatusAndPayDateBetween(
                        @Param("organizationId") String organizationId,
                        @Param("status") PayRunStatus status,
                        @Param("fromDate") LocalDate fromDate,
                        @Param("toDate") LocalDate toDate);

        // For single pay run with organization
        @Query("SELECT pr FROM PayRun pr WHERE pr.organization.organizationId = :organizationId " +
                        "AND pr.payrunId = :payrunId")
        Optional<PayRun> findByOrganizationIdAndPayrunId(
                        @Param("organizationId") String organizationId,
                        @Param("payrunId") String payrunId);
}
