package com.infinevo.payroll.payrun;

import java.time.YearMonth;
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

    PayRunResponse get(UUID id);

    /** Newest period first; {@code status} null means every status. */
    Page<PayRunResponse> list(PayRunStatus status, Pageable pageable);

    /** The run's employee rows; {@code inclusion} null means both. */
    Page<EmployeePayRunResponse> employees(UUID id, InclusionStatus inclusion, Pageable pageable);

    /** {@code DRAFT → LOCKED}, locking the period's pay inputs first ({@code PayInputService.lock}). */
    PayRunResponse lock(UUID id);

    /** {@code DRAFT} or {@code LOCKED} {@code → CANCELLED}. The period lock stays. */
    PayRunResponse cancel(UUID id);
}
