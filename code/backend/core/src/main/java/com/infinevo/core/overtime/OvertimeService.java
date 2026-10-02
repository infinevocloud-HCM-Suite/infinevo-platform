package com.infinevo.core.overtime;

import java.io.Serial;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /api/v1/overtime} (W-39.2 spec section 4) — approved overtime, entered by an
 * administrator, written to the pay input ledger (W-19) through {@code PayInputService} in the
 * same transaction. There is no edit: cancel and re-enter (spec §2).
 */
public interface OvertimeService {

    /** Span, in days, a {@link #list} call may cover — {@code from} to {@code to} inclusive. */
    int MAX_RANGE_DAYS = 93;

    /**
     * Records one entry and posts one {@code OVERTIME} row to the pay input ledger, both in one
     * transaction: if the ledger refuses, this entry is not saved either (spec §3).
     *
     * @throws ValidationException the employee does not exist or is deleted in this tenant, the
     *     date is in the future, {@code hours} is missing, non-positive or over 24, or
     *     {@code amount} is present and not positive
     */
    OvertimeResponse record(OvertimeEntry entry);

    /**
     * Submits an overtime request with status {@link OvertimeStatus#PENDING} and source
     * {@link OvertimeSource#REQUEST} without writing to the pay input ledger (W-40.5).
     *
     * @throws ValidationException if amount is present, remarks exceed 255 chars, or standard
     *     validations fail
     */
    OvertimeResponse submit(OvertimeEntry entry);

    /**
     * Approves a {@link OvertimeStatus#PENDING} request and posts one {@code OVERTIME} row to the
     * pay input ledger (W-40.5). Idempotent if already {@link OvertimeStatus#APPROVED}.
     *
     * @throws NotFoundException no such entry in the bound tenant
     * @throws IllegalStateException the entry is {@link OvertimeStatus#REJECTED} or {@link OvertimeStatus#CANCELLED}
     */
    OvertimeResponse approve(UUID id);

    /**
     * Rejects a {@link OvertimeStatus#PENDING} request (W-40.5). Idempotent if already
     * {@link OvertimeStatus#REJECTED}. Calls no ledger methods.
     *
     * @throws NotFoundException no such entry in the bound tenant
     * @throws IllegalStateException the entry is {@link OvertimeStatus#APPROVED} or {@link OvertimeStatus#CANCELLED}
     */
    OvertimeResponse reject(UUID id);

    /**
     * Cancels an entry and reverses the ledger row it posted if approved (spec §3, W-40.5 §4).
     *
     * @throws NotFoundException no such entry in the bound tenant
     * @throws AlreadyCancelledException the entry is already {@link OvertimeStatus#CANCELLED}
     * @throws NotCancellableException the entry is {@link OvertimeStatus#REJECTED}
     */
    OvertimeResponse cancel(UUID id);

    /**
     * Entries in {@code [from, to]}, most recent first, optionally for one employee.
     *
     * @throws ValidationException {@code from} is after {@code to}, or the span exceeds
     *     {@value #MAX_RANGE_DAYS} days
     */
    List<OvertimeResponse> list(LocalDate from, LocalDate to, UUID employeeId);

    /** No such overtime entry in the bound tenant. Maps to {@code 404}. */
    class NotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFoundException(UUID id) {
            super("No overtime entry " + id + " in this tenant");
        }
    }

    /** The request cannot be applied. Maps to {@code 400} with per-field detail. */
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

    /** The entry is already cancelled. Maps to {@code 409}. */
    class AlreadyCancelledException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public AlreadyCancelledException(UUID id) {
            super("Overtime entry " + id + " is already cancelled");
        }
    }

    /** The entry cannot be cancelled (e.g. it was rejected). Maps to {@code 409}. */
    class NotCancellableException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotCancellableException(UUID id) {
            super("Overtime entry " + id + " cannot be cancelled");
        }
    }
}
