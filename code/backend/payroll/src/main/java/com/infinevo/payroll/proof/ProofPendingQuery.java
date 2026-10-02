package com.infinevo.payroll.proof;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Common query interface for proof-of-investment chase list, reminders, and summary (W-34.3).
 *
 * <p>Ensures the reminder audience resolver and the chase list evaluate "pending" identically.
 */
public interface ProofPendingQuery {

    /**
     * Resolves IDs of active employees in the tenant who have submitted their declaration
     * for the given financial year, but whose proof is either not started or in DRAFT/REJECTED state.
     */
    List<UUID> findPendingEmployeeIds(UUID tenantId, String financialYear);

    /**
     * Paginated chase list of employees with a submitted declaration for the financial year.
     *
     * @param tenantId tenant identifier
     * @param financialYear financial year (e.g. "2026-2027")
     * @param statusFilter optional status filter (NOT_STARTED, DRAFT, SUBMITTED, APPROVED, REJECTED)
     * @param searchPrefix optional search prefix matching firstName, lastName, or employeeNumber
     * @param pageable pagination parameters
     */
    Page<ProofChaseRow> findChaseRows(
            UUID tenantId, String financialYear, String statusFilter, String searchPrefix, Pageable pageable);

    /**
     * Returns summary status counts and window details for the given financial year.
     */
    ProofChaseSummary getSummary(UUID tenantId, String financialYear);
}
