package com.infinevo.payroll.payrun;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every finder takes {@code tenantId} (DEBT-022). */
public interface EmployeePayRunLineRepository extends JpaRepository<EmployeePayRunLine, UUID> {

    /**
     * One employee's lines, in one statement, inside that employee's transaction (W-29.4 §4): a
     * recomputed row starts clean, and a row a resumed attempt skips keeps its lines.
     */
    @Modifying
    @Query("DELETE FROM EmployeePayRunLine l WHERE l.tenantId = :tenantId AND l.employeePayrunId = :employeePayrunId")
    int deleteByTenantIdAndEmployeePayrunId(
            @Param("tenantId") UUID tenantId, @Param("employeePayrunId") UUID employeePayrunId);

    List<EmployeePayRunLine> findByTenantIdAndEmployeePayrunIdOrderBySortOrderAsc(UUID tenantId, UUID employeePayrunId);

    long countByTenantIdAndPayrunId(UUID tenantId, UUID payrunId);

    /**
     * Year-to-date tax sum across computed, approved, or paid runs within the given period range (W-36.1 §4).
     */
    @Query(
            """
        SELECT COALESCE(SUM(l.amount), 0)
        FROM EmployeePayRunLine l, EmployeePayRun epr, PayRun pr
        WHERE l.employeePayrunId = epr.id
          AND l.payrunId = pr.id
          AND l.tenantId = :tenantId
          AND epr.tenantId = :tenantId
          AND pr.tenantId = :tenantId
          AND epr.employeeId = :employeeId
          AND l.source = com.infinevo.payroll.payrun.LineSource.TAX
          AND pr.period >= :periodFrom
          AND pr.period <= :periodTo
          AND pr.status IN (
              com.infinevo.payroll.payrun.PayRunStatus.COMPUTED,
              com.infinevo.payroll.payrun.PayRunStatus.APPROVED,
              com.infinevo.payroll.payrun.PayRunStatus.PAID
          )
          AND (:excludingPayrunId IS NULL OR pr.id <> :excludingPayrunId)
    """)
    BigDecimal sumTaxLines(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("periodFrom") String periodFrom,
            @Param("periodTo") String periodTo,
            @Param("excludingPayrunId") UUID excludingPayrunId);
}
