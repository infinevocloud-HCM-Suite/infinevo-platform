package com.infinevo.core.employee;

import java.io.Serial;
import java.util.Map;
import java.util.UUID;

/**
 * The employee record use cases (W-13.1, spec section 4).
 *
 * <p>No method takes a tenant. Every one reads it from {@code TenantContext}, which the binding
 * filter set from the verified token — spec section 3. A tenant parameter here would reappear as a
 * path variable or a header on the controller, and the isolation would then be a convention rather
 * than a boundary.
 *
 * <p>All logic lives in {@link EmployeeServiceImpl}; this interface carries the contract and the
 * three failures a caller can act on — {@code docs/CONVENTIONS.md} section 3.
 */
public interface EmployeeService {

    /** Creates an employee in the bound tenant. */
    EmployeeResponse create(EmployeeRequest request);

    /** The employee with this id in the bound tenant. Soft-deleted rows are not found. */
    EmployeeResponse get(UUID id);

    /** Replaces the mutable fields of an employee in the bound tenant. */
    EmployeeResponse update(UUID id, EmployeeRequest request);

    /**
     * Soft-deletes an employee in the bound tenant: the row stays and stops being readable.
     *
     * <p>Idempotent from the caller's point of view only in the sense that a second call raises
     * {@link NotFoundException} — the employee is already gone as far as every read path is
     * concerned.
     */
    void delete(UUID id);

    /** No such employee in the bound tenant, or it has been soft-deleted. Maps to {@code 404}. */
    class NotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFoundException(UUID id) {
            super("No employee " + id + " in this tenant");
        }
    }

    /**
     * The request cannot be applied. Maps to {@code 400} with per-field detail.
     *
     * <p>Thrown by the service rather than by a bean-validation annotation on the DTO, so the rules
     * are unit-testable with no Spring context and hold for any caller, not only an HTTP one.
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
     * This tenant already uses this employee number. Maps to {@code 409}.
     *
     * <p>Named as a conflict rather than surfacing a constraint-violation stack trace, and scoped to
     * the tenant: the index is {@code (tenant_id, employee_number)}, so the same number in another
     * tenant is legal and must never produce this — spec section 9.
     */
    class DuplicateEmployeeNumberException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public DuplicateEmployeeNumberException(String employeeNumber) {
            super("Employee number " + employeeNumber + " is already in use in this tenant");
        }
    }
}
