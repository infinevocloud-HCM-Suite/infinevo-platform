package com.infinevo.payroll.deduction;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Ad-hoc salary deductions (W-35.2). Every method works in the bound tenant. There is no edit: a
 * deduction is reversed and entered again (§13 decision 2).
 */
public interface EmployeeDeductionService {

    /**
     * Enters a batch of 1–500 deductions in one transaction: each line is a {@code POSTED} row and one
     * {@code AD_HOC_DEDUCTION} ledger row, posted to the line's period or, if that is locked, the next
     * open one. All or nothing.
     *
     * @throws EmployeeDeductionValidationException naming the first failing line; nothing is written
     */
    EmployeeDeductionBatchResponse enter(List<EmployeeDeductionLineRequest> lines);

    /**
     * Reverses the deduction's ledger row and marks it {@code REVERSED}; the ledger decides the
     * reversal's period.
     *
     * @throws EmployeeDeductionNotFoundException no such deduction in the tenant
     * @throws DeductionAlreadyReversedException it is already reversed
     * @throws EmployeeDeductionValidationException the reason is blank or too long
     */
    EmployeeDeductionResponse reverse(UUID id, String reason);

    EmployeeDeductionResponse get(UUID id);

    /** Newest period first; any null filter means every value. */
    Page<EmployeeDeductionResponse> list(
            UUID employeeId, YearMonth period, DeductionState status, DeductionType deductionType, Pageable pageable);

    /**
     * The caller's own deductions, newest period first, reversed ones included.
     *
     * @throws org.springframework.security.access.AccessDeniedException if the login is linked to no
     *     employee
     */
    List<EmployeeDeductionResponse> listOwn();
}
