package com.infinevo.payroll.priorpayroll;

import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional helper for bulk prior payroll import operations (W-38.1 §4 &amp; §9).
 *
 * <p>Each method executes in an independent {@code REQUIRES_NEW} transaction so that
 * an individual row failure (e.g. duplicate constraint violation) does not roll back
 * previously committed rows or abort the remaining imports.
 */
@Component
public class PriorPayrollImportWriter {

    private final PriorPayrollImportLogRepository importLogRepository;
    private final PriorPayrollMonthRepository monthRepository;

    public PriorPayrollImportWriter(
            PriorPayrollImportLogRepository importLogRepository, PriorPayrollMonthRepository monthRepository) {
        this.importLogRepository = Objects.requireNonNull(importLogRepository, "importLogRepository must not be null");
        this.monthRepository = Objects.requireNonNull(monthRepository, "monthRepository must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PriorPayrollImportLog saveLog(PriorPayrollImportLog log) {
        return importLogRepository.saveAndFlush(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PriorPayrollMonth insertOne(PriorPayrollMonth month) {
        return monthRepository.saveAndFlush(month);
    }
}
