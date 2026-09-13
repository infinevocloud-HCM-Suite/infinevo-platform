package com.itsdev.payroll.repository.payruns;

import com.itsdev.payroll.entity.payruns.PayRun;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.itsdev.payroll.dto.payruns.EmployeePayslipDTO;
import com.itsdev.payroll.entity.payruns.EmployeePayRun;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeePayRunRepository extends JpaRepository<EmployeePayRun, Long> {

        // Find all employee pay runs for a specific payrunId
        List<EmployeePayRun> findByPayrunId(String payrunId);

        // Optional: find by employeeId and payrunId
        List<EmployeePayRun> findByEmployeeIdAndPayrunId(String employeeId, String payrunId);

        List<EmployeePayRun> findByPayRunAndEmployeeIdIn(PayRun payRun, List<String> employeeIds);

        Page<EmployeePayRun> findByPayRun_Organization_OrganizationIdAndEmployeeIdAndPaymentStatus(
                        String organizationId,
                        String employeeId,
                        String paymentStatus,
                        Pageable pageable);

        // 📘 Fetch paginated payslips with a year filter (between two dates)
        Page<EmployeePayRun> findByPayRun_Organization_OrganizationIdAndEmployeeIdAndPaymentStatusAndPayRun_PayDateBetween(
                        String organizationId,
                        String employeeId,
                        String paymentStatus,
                        LocalDate startDate,
                        LocalDate endDate,
                        Pageable pageable);

        /**
         * Month-wise aggregated payroll summary for the given org and date range.
         * Uses exact DB column names (camelCase).
         */
        @Query(value = """
                            SELECT DATE_FORMAT(pr.payPeriodStartDate, '%Y-%m') AS month_name,
                                   SUM(epr.totalEarnings) AS total_earnings,
                                   SUM(epr.totalTaxes) AS total_taxes,
                                   SUM(epr.netPay) AS total_net_pay,
                                   SUM(epr.totalDeductions) AS total_deductions
                            FROM employee_payruns epr
                            JOIN payruns pr ON epr.payrunId = pr.payrunId
                            JOIN organization o ON pr.organizationId = o.id
                            WHERE o.organizationId = :orgId
                              AND pr.payPeriodStartDate >= :fromDate
                              AND pr.payPeriodEndDate <= :toDate
                            GROUP BY month_name
                            ORDER BY month_name ASC
                        """, nativeQuery = true)
        List<Object[]> findMonthWisePayrollSummary(@Param("orgId") String organizationId,
                        @Param("fromDate") LocalDate fromDate,
                        @Param("toDate") LocalDate toDate);

        /**
         * Sum of total taxes for the organization between two dates (based on payDate).
         */
        @Query(value = """
                            SELECT SUM(epr.totalTaxes)
                            FROM employee_payruns epr
                            JOIN payruns pr ON epr.payrunId = pr.payrunId
                            JOIN organization o ON pr.organizationId = o.id
                            WHERE o.organizationId = :organizationId
                              AND pr.payDate BETWEEN :fromDate AND :toDate
                        """, nativeQuery = true)
        Double sumTotalTaxesForOrgBetween(@Param("organizationId") String organizationId,
                        @Param("fromDate") LocalDate fromDate,
                        @Param("toDate") LocalDate toDate);

        @Query("""
                            SELECT epr
                            FROM EmployeePayRun epr
                            JOIN epr.payRun pr
                            JOIN pr.organization org
                            WHERE org.organizationId = :organizationId
                              AND epr.employeeId = :employeeId
                              AND pr.payrunId = :payrunId
                        """)
        Optional<EmployeePayRun> findEmployeePayRun(
                        @Param("organizationId") String organizationId,
                        @Param("employeeId") String employeeId,
                        @Param("payrunId") String payrunId);

        @Query("""
                            SELECT epr
                            FROM EmployeePayRun epr
                            JOIN epr.payRun pr
                            JOIN epr.employee emp
                            WHERE pr = :payRun
                              AND emp.employeeId = :employeeId
                        """)
        Optional<EmployeePayRun> findByPayRunAndEmployeeId(
                        @Param("payRun") PayRun payRun,
                        @Param("employeeId") String employeeId);
        

            @Query("""
                SELECT COALESCE(SUM(e.monthlyTds), 0)
                FROM EmployeePayRun e
                WHERE e.employeeId = :employeeId
                  AND e.payRun.payDate BETWEEN :fyStart AND :fyEnd
            """)
            Optional<BigDecimal> sumMonthlyTdsByEmployeeAndFyRange(
                    @Param("employeeId") String employeeId,
                    @Param("fyStart") LocalDate fyStart,
                    @Param("fyEnd") LocalDate fyEnd
            );

            // ✅ New method to sum monthlyTds for an employee in an organization over a fiscal year range
            @Query("""
                      SELECT COALESCE(SUM(e.monthlyTds), 0)
                      FROM EmployeePayRun e
                      JOIN e.payRun pr
                      JOIN pr.organization org
                      WHERE org.organizationId = :organizationId
                        AND e.employeeId = :employeeId
                        AND pr.payDate BETWEEN :fyStart AND :fyEnd
                  """)
                  Optional<BigDecimal> sumMonthlyTdsByOrgEmployeeAndFyRange(
                          @Param("organizationId") String organizationId,
                          @Param("employeeId") String employeeId,
                          @Param("fyStart") LocalDate fyStart,
                          @Param("fyEnd") LocalDate fyEnd
                  );




}
