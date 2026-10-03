package com.infinevo.payroll.dashboard;

import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.SkipReason;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dashboard's aggregate reads (W-37 §4) over {@code payroll.payrun}, {@code payroll.employee_payrun}
 * and {@code payroll.employee_payrun_line}. Each method is one JPQL statement bound to {@code tenant_id}
 * and summed in the database — never a {@code findAll} filtered in Java (legacy
 * {@code DashboardServiceImpl.java:225-232}, DEBT-022). It adds no finder to W-29's repositories.
 */
@Repository
@Transactional(readOnly = true)
public class DashboardQueryRepository {

    /** One run as the cards and month rows need it; amounts as stored, at scale 4. */
    public record RunRow(
            UUID id,
            String period,
            PayRunStatus status,
            LocalDate payDate,
            LocalDate paidOn,
            int included,
            int skipped,
            int progressDone,
            int progressTotal,
            BigDecimal gross,
            BigDecimal deductions,
            BigDecimal netPay) {}

    /** The sum of one component code, kind and source over a year's paid runs. */
    public record LineSum(String componentCode, LineKind lineKind, LineSource source, BigDecimal amount) {}

    private static final String RUN_COLUMNS = "SELECT r.id, r.period, r.status, r.payDate, r.paidOn, r.includedCount,"
            + " r.skippedCount, r.progressDone, r.progressTotal, r.totalGross, r.totalDeductions, r.totalNetPay"
            + " FROM PayRun r";

    @PersistenceContext
    private EntityManager entityManager;

    /** The tenant's newest non-cancelled runs, newest period first — the current run is the first. */
    public List<RunRow> recentRuns(UUID tenantId, int limit) {
        return entityManager
                .createQuery(
                        RUN_COLUMNS + " WHERE r.tenantId = :tenantId AND r.status <> :cancelled"
                                + " ORDER BY r.period DESC, r.createdAt DESC, r.id DESC",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("cancelled", PayRunStatus.CANCELLED)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(DashboardQueryRepository::toRunRow)
                .toList();
    }

    /** The run's {@code SKIPPED} rows counted by reason (W-29.1); a reason with none is absent. */
    public Map<SkipReason, Long> skippedByReason(UUID tenantId, UUID payrunId) {
        List<Object[]> rows = entityManager
                .createQuery(
                        "SELECT e.skipReason, COUNT(e) FROM EmployeePayRun e WHERE e.tenantId = :tenantId"
                                + " AND e.payrunId = :payrunId AND e.inclusionStatus = :skipped"
                                + " AND e.skipReason IS NOT NULL GROUP BY e.skipReason",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("payrunId", payrunId)
                .setParameter("skipped", InclusionStatus.SKIPPED)
                .getResultList();
        Map<SkipReason, Long> result = new EnumMap<>(SkipReason.class);
        for (Object[] row : rows) {
            result.put((SkipReason) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    /** Runs whose period lies in {@code [fromPeriod, toPeriod]} (YYYY-MM) with one of {@code statuses}, in period order. */
    public List<RunRow> runsInPeriods(
            UUID tenantId, String fromPeriod, String toPeriod, Collection<PayRunStatus> statuses) {
        return entityManager
                .createQuery(
                        RUN_COLUMNS + " WHERE r.tenantId = :tenantId AND r.period BETWEEN :fromPeriod AND :toPeriod"
                                + " AND r.status IN (:statuses) ORDER BY r.period ASC, r.createdAt ASC, r.id ASC",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("fromPeriod", fromPeriod)
                .setParameter("toPeriod", toPeriod)
                .setParameter("statuses", statuses)
                .getResultList()
                .stream()
                .map(DashboardQueryRepository::toRunRow)
                .toList();
    }

    /** Σ amount of every {@code source = 'TAX'} line, per run (W-36.1); a run with none is absent. */
    public Map<UUID, BigDecimal> taxByRun(UUID tenantId, Collection<UUID> payrunIds) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        if (payrunIds.isEmpty()) {
            return result;
        }
        List<Object[]> rows = entityManager
                .createQuery(
                        "SELECT l.payrunId, SUM(l.amount) FROM EmployeePayRunLine l WHERE l.tenantId = :tenantId"
                                + " AND l.payrunId IN (:payrunIds) AND l.source = :tax GROUP BY l.payrunId",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("payrunIds", payrunIds)
                .setParameter("tax", LineSource.TAX)
                .getResultList();
        for (Object[] row : rows) {
            result.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return result;
    }

    /**
     * The lines of {@code PAID} runs in {@code [fromPeriod, toPeriod]} from the given sources, summed by
     * component code, kind and source — what the statutory tiles group (W-37 §4).
     */
    public List<LineSum> paidLineSums(
            UUID tenantId, String fromPeriod, String toPeriod, Collection<LineSource> sources) {
        return entityManager
                .createQuery(
                        "SELECT l.componentCode, l.lineKind, l.source, SUM(l.amount) FROM EmployeePayRunLine l"
                                + " JOIN PayRun r ON r.id = l.payrunId AND r.tenantId = l.tenantId"
                                + " WHERE l.tenantId = :tenantId AND r.tenantId = :tenantId AND r.status = :paid"
                                + " AND r.period BETWEEN :fromPeriod AND :toPeriod AND l.source IN (:sources)"
                                + " GROUP BY l.componentCode, l.lineKind, l.source",
                        Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("paid", PayRunStatus.PAID)
                .setParameter("fromPeriod", fromPeriod)
                .setParameter("toPeriod", toPeriod)
                .setParameter("sources", sources)
                .getResultList()
                .stream()
                .map(row -> new LineSum((String) row[0], (LineKind) row[1], (LineSource) row[2], (BigDecimal) row[3]))
                .toList();
    }

    private static RunRow toRunRow(Object[] row) {
        return new RunRow(
                (UUID) row[0],
                (String) row[1],
                (PayRunStatus) row[2],
                (LocalDate) row[3],
                (LocalDate) row[4],
                ((Number) row[5]).intValue(),
                ((Number) row[6]).intValue(),
                ((Number) row[7]).intValue(),
                ((Number) row[8]).intValue(),
                (BigDecimal) row[9],
                (BigDecimal) row[10],
                (BigDecimal) row[11]);
    }
}
