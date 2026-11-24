package com.infinevo.core.payinput;

import java.io.Serial;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The pay input ledger (W-19) — the one seam any module writes a pay-affecting value through, and
 * the pay run reads ({@code 12-core-contracts.md} §3 row 100). No method takes a tenant; every one
 * reads it from {@code TenantContext}, the rule {@code DocumentService} follows.
 *
 * <p><strong>Keep it dumb.</strong> This is a ledger, not a calculation engine
 * ({@code 09-build-order.md:200}). Nothing here computes a rate, prorates a value or applies a
 * policy — the one arithmetic allowed is the signed sum behind {@link PayInputListResponse#totalsByKind}.
 */
public interface PayInputService {

    /**
     * Inserts one row. {@code amount} and {@code quantity} must each be positive when given — the
     * kind carries the sign (spec §4).
     *
     * <p>Untagged ({@code command.runRef()} is {@code null}): posted where {@link #posting} says
     * (D-144). An OPEN or LOCKED month takes the row itself; a CLOSED month's row is posted to the
     * first month after it that is not CLOSED, and the response's {@code postedPeriod} says so. This
     * is the path for writes no person is looking at (a leave approval, a scheduled instalment, a
     * claim's outcome); a hand entry goes through {@link #recordEntry}, which refuses a CLOSED month
     * instead. Tagged (W-30.1): the period lock is not consulted at all; the row is refused
     * outright, not redirected, if its run is locked — there is no "next" off-cycle run to try.
     *
     * @throws ValidationException a non-positive {@code amount} or {@code quantity}
     * @throws DuplicatePayInputException {@code (tenant, sourceModule, sourceRef)} already names a
     *     non-reversal row ({@code 12-core-contracts.md} §6 decision 2)
     * @throws RunLockedException {@code command.runRef()} names a locked run (W-30.1)
     */
    PayInputResponse record(PayInputCommand command);

    /**
     * {@link #record}, for an entry a person typed (D-144, founder 2026-11-23): an untagged entry for
     * a CLOSED month is refused, not moved, so the person can enter it for the month the message
     * names. OPEN and LOCKED months, and tagged rows, behave exactly as in {@link #record}.
     *
     * @throws PeriodClosedException {@code command.period()} is CLOSED and the row is untagged
     */
    PayInputResponse recordEntry(PayInputCommand command);

    /**
     * Where an untagged entry for {@code period} would post, and why (D-144) — the same rule
     * {@link #record} and {@link #recordEntry} apply, read without writing.
     */
    PayInputPosting posting(YearMonth period);

    /** One employee's untagged rows for a period, plus a total per kind (W-30.1: tagged rows excluded). */
    PayInputListResponse forEmployee(UUID employeeId, YearMonth period);

    /**
     * Every employee's untagged rows for a period, in one statement — the batch read the pay run
     * needs. Tagged rows are excluded (W-30.1): a bonus paid off-cycle must not be paid again here.
     */
    PayInputListResponse forPeriod(YearMonth period);

    /**
     * Every row tagged to one run, all employees, one statement, plus a quantity and an amount
     * total per kind broken down by employee (W-30.1) — the batch read W-30.2 needs to price each
     * employee's own line, not a run-wide blend across all of them.
     */
    PayInputRunResponse forRun(UUID runRef);

    /** {@link #lock(YearMonth, PayInputLockStage)} at {@link PayInputLockStage#LOCKED} — the call every caller made before D-144. */
    default void lock(YearMonth period) {
        lock(period, PayInputLockStage.LOCKED);
    }

    /**
     * Locks a period at {@code stage} (D-144). LOCKED: entries still post there and the run must be
     * recomputed. CLOSED: untagged entries are refused (or, through {@link #record}, moved on).
     * A LOCKED lock is raised to CLOSED by a CLOSED call; a CLOSED lock is never lowered, and
     * locking at the stage a month already has is a no-op.
     */
    void lock(YearMonth period, PayInputLockStage stage);

    /**
     * How many untagged entries for {@code period} were written after {@code since} and change what
     * a run computed at {@code since} would pay (D-144) - a row and its own reversal both written
     * after it count as nothing. The pay run refuses approval while this is above zero: a LOCKED
     * month takes entries, and the founder's rule is that the run must then be recomputed.
     */
    long countChangesSince(YearMonth period, Instant since);

    /**
     * The stage of the period's lock, or empty when it has none (D-144). A caller that must not fail
     * on a CLOSED month reads this before {@link #unlock}: an exception thrown out of a
     * transactional call marks the caller's transaction rollback-only even when it is caught.
     */
    Optional<PayInputLockStage> periodStage(YearMonth period);

    /**
     * Removes the period's LOCKED lock — its run was cancelled (D-144, D-138). No lock is a no-op.
     *
     * @return {@code true} if a lock was removed
     * @throws PeriodClosedException the month is CLOSED; that lock is never removed
     */
    boolean unlock(YearMonth period);

    /**
     * Locks a run so no further write tagged to it succeeds (W-30.1). {@code period} is stored
     * alongside the lock for reporting; it does not lock the period itself — an untagged input for
     * the same calendar period is unaffected. Locking the same run twice is a no-op.
     */
    void lockRun(UUID runRef, YearMonth period);

    /**
     * Inserts a new row that reverses {@code id} — same employee, period, kind, quantity and amount,
     * {@code reversesId} set. A wrong input is corrected this way, never edited (spec §4); a reversal
     * of a row in a locked period is redirected exactly as a fresh input would be.
     *
     * <p>A reversal of a row tagged to a run (W-30.1) stays tagged to it, as long as that run is
     * still open. If the run has since been locked, the reversal is not new collection into it —
     * it is a correction of what the run already collected, and a mistake must stay correctable —
     * so it falls back to an ordinary, untagged correction, redirected the same way a locked
     * period's reversal is. It never throws {@link RunLockedException}.
     *
     * @throws NotFoundException no such row in the bound tenant
     */
    PayInputResponse reverse(UUID id, String reason);

    /** No such pay input in the bound tenant. Maps to {@code 404}. */
    class NotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFoundException(UUID id) {
            super("No pay input " + id + " in this tenant");
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

    /**
     * {@code (tenant, sourceModule, sourceRef)} already names a non-reversal row
     * ({@code uk_pay_input_tenant_source}, {@code V031}) — a retried caller posting the same event
     * twice. Maps to {@code 409}.
     */
    class DuplicatePayInputException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public DuplicatePayInputException(String sourceModule, String sourceRef) {
            super("A pay input from " + sourceModule + " with reference " + sourceRef + " was already recorded");
        }
    }

    /**
     * {@code run_ref} names a run with a lock row ({@code core.pay_input_period_lock}, {@code V060}).
     * Not redirected the way a locked period is — there is no next off-cycle run to try (W-30.1
     * spec §3 decision 3). Maps to {@code 409}.
     */
    class RunLockedException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public RunLockedException(UUID runRef) {
            super("Run " + runRef + " is locked; this pay input was not recorded");
        }
    }

    /**
     * The month is CLOSED — its run was approved or paid (D-144). Maps to {@code 409}. The message is
     * shown to the person as it is, so it names the month to use instead.
     */
    class PeriodClosedException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

        private final YearMonth period;
        private final YearMonth firstOpenPeriod;

        private PeriodClosedException(String message, YearMonth period, YearMonth firstOpenPeriod) {
            super(message);
            this.period = period;
            this.firstOpenPeriod = firstOpenPeriod;
        }

        /** "October 2026 is closed (run approved). Enter this for November 2026." */
        public static PeriodClosedException forEntry(YearMonth period, YearMonth firstOpenPeriod) {
            return new PeriodClosedException(
                    monthName(period) + " is closed (run approved). Enter this for " + monthName(firstOpenPeriod) + ".",
                    period,
                    firstOpenPeriod);
        }

        /** A CLOSED month's lock asked to be removed. */
        public static PeriodClosedException forUnlock(YearMonth period) {
            return new PeriodClosedException(
                    monthName(period) + " is closed (run approved); its lock cannot be removed.", period, null);
        }

        /** "October 2026". */
        public static String monthName(YearMonth period) {
            return MONTH.format(period);
        }

        public YearMonth period() {
            return period;
        }

        /** The first month that is not CLOSED, or {@code null} when this came from {@link #forUnlock}. */
        public YearMonth firstOpenPeriod() {
            return firstOpenPeriod;
        }
    }

    /**
     * The row was already reversed, or is itself a reversal ({@code uk_pay_input_tenant_reverses},
     * {@code V031}). A second reversal would not restore anything — it would subtract again. Maps to
     * {@code 409}.
     */
    class AlreadyReversedException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public AlreadyReversedException(UUID id) {
            super("Pay input " + id + " was already reversed, or is itself a reversal");
        }
    }
}
