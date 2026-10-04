package com.infinevo.payroll.priorpayroll;

import com.infinevo.payroll.taxdeclaration.FinancialYear;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tax already deducted in months imported through W-38.1 (W-38.2 §4).
 *
 * <p>Read by the monthly TDS line, the TDS year-to-date figure and Form 16, so all three count the
 * same imported months. Each method is one aggregate query (DEBT-019). Writes nothing. Read-only
 * transactional so the tenant binding holds when called outside a caller's transaction; inside one
 * it joins it.
 */
@Component
@Transactional(readOnly = true)
public class PriorPayrollTaxQuery {

    private static final int SCALE = 4;

    private final PriorPayrollMonthRepository repository;

    public PriorPayrollTaxQuery(PriorPayrollMonthRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /** Imported TDS for the employee across April..March of {@code fy}; zero when nothing is imported. */
    public BigDecimal total(UUID tenantId, UUID employeeId, FinancialYear fy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(fy, "fy must not be null");
        BigDecimal sum = repository.sumTds(tenantId, employeeId, from(fy), to(fy));
        return sum == null ? BigDecimal.ZERO.setScale(SCALE) : sum;
    }

    /** Imported TDS for the employee by period ({@code YYYY-MM}) in {@code fy}; empty when nothing is imported. */
    public Map<String, BigDecimal> byPeriod(UUID tenantId, UUID employeeId, FinancialYear fy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(fy, "fy must not be null");
        List<PriorPayrollMonthRepository.PeriodTds> rows =
                repository.sumTdsByPeriod(tenantId, employeeId, from(fy), to(fy));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, BigDecimal> result = new TreeMap<>();
        for (PriorPayrollMonthRepository.PeriodTds row : rows) {
            if (row.getPeriod() != null && row.getTds() != null) {
                result.put(row.getPeriod(), row.getTds());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** The periods in {@code fy} the tenant has imported for any employee (W-38.2 §13 decision 3). */
    public Set<String> importedPeriods(UUID tenantId, FinancialYear fy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(fy, "fy must not be null");
        List<String> periods = repository.findDistinctPeriods(tenantId, from(fy), to(fy));
        return periods == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(periods));
    }

    private static String from(FinancialYear fy) {
        return String.format("%04d-04", fy.startYear());
    }

    private static String to(FinancialYear fy) {
        return String.format("%04d-03", fy.endYear());
    }
}
