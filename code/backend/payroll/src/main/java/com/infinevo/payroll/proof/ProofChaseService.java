package com.infinevo.payroll.proof;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for officer chase list and summary of employee proof-of-investment submissions (W-34.3).
 */
public interface ProofChaseService {

    /**
     * Lists employees who have submitted an investment declaration for the financial year,
     * along with their proof status and totals.
     */
    Page<ProofChaseRow> list(String financialYear, String status, String search, Pageable pageable);

    /**
     * Returns summary counts and proof window status for the financial year.
     */
    ProofChaseSummary summary(String financialYear);
}
