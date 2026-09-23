package com.infinevo.core.org;

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The four use cases every org master has (W-14.1, spec section 4), and the four failures a caller
 * can act on.
 *
 * <p>{@link DepartmentService}, {@link DesignationService} and {@link WorkLocationService} each
 * extend this with their own request and response types, so a caller holds a service that speaks one
 * master and cannot pass a {@link DepartmentRequest} to a designation. The contract is stated once
 * because it genuinely is one contract — spec section 4 lists the same four rows three times.
 *
 * <p>No method takes a tenant. Every one reads it from {@code TenantContext}, which the binding
 * filter set from the verified token — spec section 3. A tenant parameter here would reappear as a
 * path variable or a header on the controller, and the isolation would then be a convention rather
 * than a boundary.
 *
 * <p>All logic lives in {@link AbstractOrgMasterServiceImpl} and the three classes that extend it —
 * {@code docs/CONVENTIONS.md} section 3.
 *
 * @param <Q> the request type for this master
 * @param <R> the response type for this master
 */
public interface OrgMasterService<Q extends OrgMasterRequest, R> {

    /** Creates a record in the bound tenant. */
    R create(Q request);

    /**
     * Every record in the bound tenant, ordered by code.
     *
     * @param activeOnly true to return only the records still available for a new assignment
     */
    List<R> list(boolean activeOnly);

    /** Replaces the mutable fields of a record in the bound tenant. */
    R update(UUID id, Q request);

    /**
     * Deletes a record in the bound tenant — a hard delete, and refused while an employee points at
     * it ({@link RecordInUseException}).
     *
     * <p>Hard, unlike {@code EmployeeService.delete}, and the difference is deliberate. An employee
     * is history a pay run has to be reproducible from; a department nobody holds is a list entry
     * somebody mistyped. Deactivating is the supported way to retire one that is in use — spec
     * section 4.
     */
    void delete(UUID id);

    /** No such record in the bound tenant. Maps to {@code 404}. */
    class NotFoundException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFoundException(String kind, UUID id) {
            super("No " + kind + " " + id + " in this tenant");
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
     * This tenant already uses this code for this master. Maps to {@code 409}.
     *
     * <p>Named as a conflict rather than surfacing a constraint-violation stack trace, and scoped to
     * the tenant: the index is {@code (tenant_id, code)}, so the same code in another tenant is legal
     * and must never produce this.
     */
    class DuplicateCodeException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public DuplicateCodeException(String kind, String code) {
            super("A " + kind + " with code " + code + " already exists in this tenant");
        }
    }

    /**
     * The record cannot be deleted because employees point at it. Maps to {@code 409}.
     *
     * <p>The message names the alternative, because there always is one: deactivate the record and
     * it disappears from new assignments while the employees holding it keep working — spec section
     * 4.
     */
    class RecordInUseException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public RecordInUseException(String kind, long assigned) {
            super("This " + kind + " cannot be deleted: " + assigned
                    + (assigned == 1 ? " employee is" : " employees are")
                    + " assigned to it. Deactivate it instead — it then disappears from new"
                    + " assignments without changing anyone's record.");
        }
    }
}
