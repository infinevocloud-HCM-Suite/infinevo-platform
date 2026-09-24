package com.infinevo.core.employee.detail;

import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/**
 * The two use cases every employee detail section has (W-13.2, spec section 4), and the three
 * failures a caller can act on.
 *
 * <p>{@link EmployeePersonalService} and its four siblings each extend this with their own request
 * and response types, so a caller holds a service that speaks one section and cannot pass an
 * {@link EmployeeBankRequest} to the contact section. The contract is stated once because it
 * genuinely is one contract — spec section 4 lists the same two rows five times. Same shape as
 * {@code com.infinevo.core.org.OrgMasterService}.
 *
 * <p>No method takes a tenant. Every one reads it from {@code TenantContext}, which the binding
 * filter set from the verified token — spec section 3. A tenant parameter here would reappear as a
 * path variable or a header on the controller, and the isolation would then be a convention rather
 * than a boundary.
 *
 * <p>All logic lives in {@link AbstractEmployeeDetailServiceImpl} and the five classes that extend
 * it — {@code docs/CONVENTIONS.md} section 3.
 *
 * @param <Q> the request type for this section
 * @param <R> the response type for this section
 */
public interface EmployeeDetailService<Q, R> {

    /**
     * This section for this employee, in the bound tenant.
     *
     * @throws NotFoundException if the employee does not exist in the bound tenant, has been
     *     soft-deleted, or has never had this section written
     */
    R get(UUID employeeId);

    /**
     * Writes this section for this employee in the bound tenant: creates the row on the first call,
     * replaces it on every later one.
     *
     * <p>{@code PUT} and not {@code POST} — a section is part of an employee, created on first write.
     * There is no separate creation step and <strong>never a second row per section per
     * employee</strong>: the unique index on {@code (tenant_id, employee_id)} in each migration is
     * the backstop, and a violation of it is reported as {@link ConcurrentWriteException}, never as a
     * {@code 500}.
     *
     * <p>A replace, not a patch. A field omitted from the request is written as null.
     */
    R put(UUID employeeId, Q request);

    /**
     * No such employee in the bound tenant, or no such section on it. Maps to {@code 404}.
     *
     * <p>Both cases are the same exception on purpose. <strong>A section for an employee you cannot
     * see is a {@code 404} and nothing else</strong> — telling "no such employee" apart from "no bank
     * details yet" would let a caller enumerate another tenant's employee ids by watching which
     * sentence comes back.
     */
    class NotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        /** No such employee in the bound tenant, or it has been soft-deleted. */
        public NotFoundException(UUID employeeId) {
            super("No employee " + employeeId + " in this tenant");
        }

        /**
         * The employee is there, but this section has never been written.
         *
         * @param kind the section, as a caller names it — "personal", "bank"
         */
        public NotFoundException(String kind, UUID employeeId) {
            super("No " + kind + " section for employee " + employeeId + " in this tenant");
        }
    }

    /**
     * The request cannot be applied. Maps to {@code 400} with per-field detail.
     *
     * <p>Thrown by the service rather than by a bean-validation annotation on the DTO, so the rules
     * are unit-testable with no Spring context and hold for any caller, not only an HTTP one. Same
     * shape as {@code EmployeeService.ValidationException}.
     */
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
     * Two writers created this section at the same moment and the unique index refused the second.
     * Maps to {@code 409}.
     *
     * <p>This is the race the read-then-create in {@link AbstractEmployeeDetailServiceImpl#put}
     * cannot close on its own: both calls find no row, both build one, and
     * {@code (tenant_id, employee_id)} lets exactly one through. Without this the loser reaches the
     * caller as a {@code 500} carrying SQL and an index name.
     *
     * <p>Reported rather than retried. A retry would re-read the row the other writer has just
     * committed and overwrite it with a body composed before it existed — a silent lost update in
     * place of an honest conflict. The caller repeats the {@code PUT} if it still wants to win.
     */
    class ConcurrentWriteException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ConcurrentWriteException(String kind, UUID employeeId) {
            super("The " + kind + " section for employee " + employeeId
                    + " was created by another request at the same moment. Read it and try again.");
        }
    }
}
