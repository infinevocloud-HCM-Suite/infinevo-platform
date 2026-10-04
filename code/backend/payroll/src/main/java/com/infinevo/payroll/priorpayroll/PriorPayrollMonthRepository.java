package com.infinevo.payroll.priorpayroll;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PriorPayrollMonth} (W-38.1 §4).
 * Every query is tenant-scoped per DEBT-022.
 */
@Repository
public interface PriorPayrollMonthRepository extends JpaRepository<PriorPayrollMonth, UUID> {

    boolean existsByTenantIdAndPeriod(UUID tenantId, String period);

    boolean existsByTenantId(UUID tenantId);

    boolean existsByTenantIdAndEmployeeIdAndPeriod(UUID tenantId, UUID employeeId, String period);

    Optional<PriorPayrollMonth> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<PriorPayrollMonth> findByTenantIdAndPeriodBetween(
            UUID tenantId, String fromPeriod, String toPeriod, Pageable pageable);

    Page<PriorPayrollMonth> findByTenantIdAndEmployeeIdAndPeriodBetween(
            UUID tenantId, UUID employeeId, String fromPeriod, String toPeriod, Pageable pageable);

    @Query("SELECT DISTINCT m.period FROM PriorPayrollMonth m WHERE m.tenantId = :tenantId "
            + "AND m.period >= :fromPeriod AND m.period <= :toPeriod ORDER BY m.period ASC")
    List<String> findDistinctPeriods(
            @Param("tenantId") UUID tenantId,
            @Param("fromPeriod") String fromPeriod,
            @Param("toPeriod") String toPeriod);

    long deleteByIdAndTenantId(UUID id, UUID tenantId);

    /** Imported TDS for one employee over a period range, one aggregate (W-38.2 §4, DEBT-019). */
    @Query("SELECT COALESCE(SUM(m.tds), 0) FROM PriorPayrollMonth m WHERE m.tenantId = :tenantId "
            + "AND m.employeeId = :employeeId AND m.period >= :fromPeriod AND m.period <= :toPeriod")
    BigDecimal sumTds(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("fromPeriod") String fromPeriod,
            @Param("toPeriod") String toPeriod);

    /** Imported TDS for one employee grouped by period, one aggregate (W-38.2 §4, DEBT-019). */
    @Query("SELECT m.period AS period, COALESCE(SUM(m.tds), 0) AS tds FROM PriorPayrollMonth m "
            + "WHERE m.tenantId = :tenantId AND m.employeeId = :employeeId "
            + "AND m.period >= :fromPeriod AND m.period <= :toPeriod GROUP BY m.period ORDER BY m.period ASC")
    List<PeriodTds> sumTdsByPeriod(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("fromPeriod") String fromPeriod,
            @Param("toPeriod") String toPeriod);

    /** Projection for {@link #sumTdsByPeriod}. */
    interface PeriodTds {
        String getPeriod();

        BigDecimal getTds();
    }
}
