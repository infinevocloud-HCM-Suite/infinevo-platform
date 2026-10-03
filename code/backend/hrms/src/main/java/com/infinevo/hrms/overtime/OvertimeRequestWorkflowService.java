package com.infinevo.hrms.overtime;

import com.infinevo.core.overtime.OvertimeResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * The employee's side of overtime (W-40.6 §4): ask for it, read my requests. Approving and rejecting go through
 * {@code core}'s {@code POST /api/v1/approvals/steps/{stepId}/decide}; the outcome reaches the row through
 * {@link OvertimeRequestOutcomeHandler}.
 */
public interface OvertimeRequestWorkflowService {

    /** The polymorphic subject table the approval instance points at. */
    String SUBJECT_TABLE = "core.overtime_request";

    /**
     * Saves a {@code PENDING} request for the caller through {@code OvertimeService.submit} and starts the
     * {@code OVERTIME} approval flow, in one transaction: if the engine refuses, the row is not saved.
     *
     * @throws com.infinevo.core.overtime.OvertimeService.ValidationException the date is in the future, hours are not
     *     in {@code (0, 24]}, or remarks are too long
     * @throws IllegalStateException the tenant has no active {@code OVERTIME} approval definition
     * @throws org.springframework.security.access.AccessDeniedException the login is not linked to an employee
     */
    OvertimeResponse submit(OvertimeRequestSubmission submission);

    /**
     * The caller's requests of every status whose date is in {@code [from, to]}, newest first.
     *
     * @throws com.infinevo.core.overtime.OvertimeService.ValidationException {@code from} is after {@code to}, or the
     *     span exceeds 93 days
     * @throws org.springframework.security.access.AccessDeniedException the login is not linked to an employee
     */
    List<OvertimeResponse> mine(LocalDate from, LocalDate to);
}
