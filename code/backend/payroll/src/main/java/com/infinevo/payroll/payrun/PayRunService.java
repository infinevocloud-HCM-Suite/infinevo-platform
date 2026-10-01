package com.infinevo.payroll.payrun;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Pay run creation, inclusion and locking (W-29.1). Every method works in the bound tenant. Status
 * changes are methods, never a field in a request.
 */
public interface PayRunService {

    /**
     * Creates the {@code DRAFT} run for {@code period} with one row per considered employee.
     *
     * @throws DuplicatePayRunException if a non-cancelled run exists for the period
     * @throws com.infinevo.payroll.schedule.NoPayScheduleException if the tenant has no pay schedule
     * @throws IllegalArgumentException if the period is before the schedule's first period
     */
    PayRunResponse create(YearMonth period);

    /**
     * Creates a {@code DRAFT} off-cycle run (W-30.2 §3) for the named employees, in the pay date's
     * month: bank details required, salary not. Any number of off-cycle runs may exist for a month.
     *
     * @throws EmployeeNotInRunException naming every employee not found or not employed in the period
     * @throws com.infinevo.payroll.schedule.NoPayScheduleException if the tenant has no pay schedule
     * @throws IllegalArgumentException if no employee is named, the pay date is missing or before the
     *     schedule's first period, or the note is longer than 500 characters
     */
    PayRunResponse createOffCycle(LocalDate payDate, List<UUID> employeeIds, String notes);

    /**
     * Records each item on the ledger tagged with the off-cycle run (W-30.2 §3) — one result per item,
     * in order. Every item is checked before any is written.
     *
     * @throws NotAnOffCycleRunException unless the run is off-cycle and {@code DRAFT}
     * @throws EmployeeNotInRunException naming every employee who is not an included row of the run
     * @throws IllegalArgumentException for an empty list, a missing field, {@code LOP_DAYS}, an amount
     *     not above zero, or a reference longer than {@link #MAX_INPUT_SOURCE_REF_LENGTH}
     */
    List<PayRunInputResponse> addInputs(UUID id, List<PayRunInputRequest> inputs);

    /**
     * The officer's reference fits the ledger's 64-character {@code source_ref} once the run prefixes it
     * with {@code payrun:<id>:} (44 characters).
     */
    int MAX_INPUT_SOURCE_REF_LENGTH = 20;

    PayRunResponse get(UUID id);

    /** Newest period first; {@code status} null means every status. */
    default Page<PayRunResponse> list(PayRunStatus status, Pageable pageable) {
        return list(status, null, pageable);
    }

    /** Newest period first; a null {@code status} or {@code runType} means every one (W-30.2). */
    Page<PayRunResponse> list(PayRunStatus status, PayRunType runType, Pageable pageable);

    /** The run's employee rows; {@code inclusion} null means both. */
    Page<EmployeePayRunResponse> employees(UUID id, InclusionStatus inclusion, Pageable pageable);

    /**
     * {@code DRAFT → LOCKED}, locking the pay inputs first: the period's ({@code PayInputService.lock})
     * for a regular run, the run's own ({@code lockRun}) for an off-cycle one — never the month (W-30.2).
     */
    PayRunResponse lock(UUID id);

    /** {@code DRAFT} or {@code LOCKED} {@code → CANCELLED}. The period lock stays. */
    PayRunResponse cancel(UUID id);

    /**
     * {@code LOCKED | COMPUTED | FAILED → COMPUTING}, and the computation queued for the worker
     * (W-29.4 §3); returns at once. A {@code COMPUTING} run whose job has not moved for 15 minutes
     * starts a new attempt that keeps the rows the abandoned one finished.
     *
     * @throws IllegalPayRunTransitionException from {@code DRAFT}, {@code APPROVED}, {@code PAID} or
     *     {@code CANCELLED}
     * @throws PayRunComputeInProgressException from {@code COMPUTING} within the stale window
     * @throws PayRunEnqueueException if no queue is configured or the message could not be sent
     */
    ComputeAcceptedResponse compute(UUID id);

    /** One employee's lines in {@code sort_order}, with the row's computation error if any. */
    EmployeePayRunLinesResponse lines(UUID id, UUID employeeId);
}
