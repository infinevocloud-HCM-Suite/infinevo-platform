package com.infinevo.payroll.scheduled;

import java.io.Serial;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scheduled earnings (W-73.6): an officer plans a one-time or instalment earning for a future month;
 * {@link #materialise} turns the instalment due in a period into one {@code ONE_TIME_PAYOUT} pay
 * input, which the unchanged {@code PayInputLineContributor} then pays as a taxable earning line.
 * No method takes a tenant; every one reads it from {@code TenantContext}.
 */
public interface ScheduledEarningService {

    /** One employee's schedules, newest first, with the pay input each paid instalment became. */
    List<ScheduledEarningResponse> listForEmployee(UUID employeeId);

    /**
     * Schedules an earning. The component must be active and flagged Scheduled, the amount positive,
     * the first period this month or later, the instalments 1–12, the employee on the books.
     *
     * @throws ValidationException a rule above is broken
     * @throws com.infinevo.core.employee.EmployeeService.NotFoundException no such employee in the tenant
     */
    ScheduledEarningResponse create(UUID employeeId, ScheduledEarningRequest request);

    /** Holds a {@code SCHEDULED} row. @throws IllegalTransitionException from any other state */
    ScheduledEarningResponse pause(UUID id, String reason);

    /** Releases a {@code PAUSED} row. @throws IllegalTransitionException from any other state */
    ScheduledEarningResponse resume(UUID id);

    /** Stops a row for good. @throws IllegalTransitionException when it is already {@code PAID} or {@code CANCELLED} */
    ScheduledEarningResponse cancel(UUID id, String reason);

    /**
     * Writes one pay input for every {@code SCHEDULED} row whose next period is {@code period}, and
     * advances the row — the last instalment sets {@code PAID}. Idempotent: an instalment whose
     * ledger row already exists is counted, never written twice. Called when a regular run is created
     * for the period, and nightly by the worker for the current period.
     *
     * @return how many pay inputs this call wrote
     */
    int materialise(YearMonth period);

    /** Cancels every row of the employee that could still pay out, with reason {@code terminated}. */
    int cancelForTerminatedEmployee(UUID employeeId);

    /** No such scheduled earning in the bound tenant. Maps to {@code 404}. */
    class NotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFoundException(UUID id) {
            super("No scheduled earning " + id + " in this tenant");
        }
    }

    /** The request breaks a rule. Maps to {@code 400} with per-field detail. */
    class ValidationException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Map<String, String> fieldErrors;

        public ValidationException(Map<String, String> fieldErrors) {
            super("The request was not valid: " + fieldErrors);
            this.fieldErrors = Map.copyOf(fieldErrors);
        }

        public Map<String, String> fieldErrors() {
            return fieldErrors;
        }
    }

    /** The row's status does not allow the action. Maps to {@code 409}. */
    class IllegalTransitionException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public IllegalTransitionException(UUID id, ScheduledEarningStatus from, String action) {
            super("Scheduled earning " + id + " is " + from + " and cannot be " + action);
        }
    }
}
