package com.infinevo.core.employee.detail;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every rule the five employee detail sections share (W-13.2) — {@code docs/CONVENTIONS.md}
 * section 3.
 *
 * <p>The five sections differ only in their columns, so this holds the read / write flow once and
 * the five subclasses supply what actually differs: the entity, the response, the name of the
 * unique index, the field rules and the copy onto the entity. That is the whole of the abstraction —
 * there is no registry, no reflection and no configuration. Same shape, and for the same reason, as
 * {@code com.infinevo.core.org.AbstractOrgMasterServiceImpl}.
 *
 * <p>The tenant is read once per call from {@link TenantContext} and is never a parameter, never a
 * header, never a path variable. {@link TenantContext#require()} throws when none is bound, which is
 * the behaviour wanted: a query with no tenant is either a bug or a cross-tenant read.
 *
 * <p><strong>The employee is read through {@code EmployeeRepository}'s tenant-scoped,
 * soft-delete-aware finder</strong> and not through a new query of this package's own. That single
 * call is what makes "a section for an employee you cannot see is a 404" true for all five sections
 * at once, and it is also <strong>the only thing stopping a cross-tenant reference</strong>: the
 * {@code employee_id} columns carry foreign keys, but PostgreSQL runs referential-integrity checks
 * as the table owner with row security off, and the owner is {@code migration_user}. The database
 * will happily accept a detail row in tenant A pointing at an employee in tenant B —
 * {@code EmployeeDetailCascadeIT} demonstrates exactly that on a raw {@code app_user} connection, and
 * then proves this service refuses it.
 *
 * <p>Because that read happens first, the employee is already in the persistence context when a
 * response is built, so {@code entity.getEmployee().getId()} costs no extra SELECT — the N+1 hazard
 * {@code EmployeeResponse.from} documents does not arise here.
 *
 * <p>Validation is hand-written rather than annotation-driven so that it is exercised by a plain
 * JUnit test with no Spring context, and so that it holds for a caller that is not an HTTP request.
 *
 * @param <E> the entity this service owns
 * @param <Q> its request type
 * @param <R> its response type
 */
abstract class AbstractEmployeeDetailServiceImpl<E extends EmployeeDetail, Q, R>
        implements EmployeeDetailService<Q, R> {

    /** The audit columns are {@code varchar(100)} in every migration. */
    private static final int MAX_ACTOR = 100;

    /**
     * The email shape {@code EmployeeServiceImpl.validate} uses for {@code workEmail}. Deliberately
     * the same one: two definitions of "is an email address" in one codebase is how a value accepted
     * by one endpoint is refused by the next.
     */
    private static final String EMAIL_PATTERN = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+";

    private final EmployeeDetailRepository<E> repository;
    private final EmployeeRepository employees;

    protected AbstractEmployeeDetailServiceImpl(EmployeeDetailRepository<E> repository, EmployeeRepository employees) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.employees = Objects.requireNonNull(employees, "employees must not be null");
    }

    /** The word this section is called in an error message — "personal", "contact", "bank". */
    protected abstract String kind();

    /**
     * The name of the {@code (tenant_id, employee_id)} unique index, so its violation can be told
     * apart from every other integrity failure. If the index is renamed in the migration, rename it
     * here too, or a concurrent create starts surfacing as a {@code 500}.
     */
    protected abstract String uniqueEmployeeIndexName();

    /** A new, unsaved section row for this employee in this tenant. */
    protected abstract E newEntity(UUID tenantId, Employee employee, String actor);

    /** Repacks a persisted entity into this section's response. */
    protected abstract R toResponse(E entity);

    /** Checks this section's fields. Adds to {@code errors}; throws nothing. */
    protected abstract void validate(Q request, Map<String, String> errors);

    /** Copies the validated request onto the entity and stamps it. Called only after {@link #validate}. */
    protected abstract void applyTo(E entity, Q request, String actor);

    @Override
    @Transactional(readOnly = true)
    public R get(UUID employeeId) {
        UUID tenantId = TenantContext.require();
        requireEmployee(employeeId, tenantId);
        E entity = repository
                .findByTenantIdAndEmployeeId(tenantId, employeeId)
                .orElseThrow(() -> new NotFoundException(kind(), employeeId));
        return toResponse(entity);
    }

    @Override
    @Transactional
    public R put(UUID employeeId, Q request) {
        UUID tenantId = TenantContext.require();
        Employee employee = requireEmployee(employeeId, tenantId);
        check(request);

        String actor = currentActor();
        // Read first, then create only if there is nothing. This is what makes PUT create-or-replace
        // with never a second row; the unique index is the backstop for the race this read cannot
        // close, and save() below turns that into a 409 rather than a 500.
        E entity = repository
                .findByTenantIdAndEmployeeId(tenantId, employeeId)
                .orElseGet(() -> newEntity(tenantId, employee, actor));
        applyTo(entity, request, actor);
        return toResponse(save(entity, employeeId));
    }

    /**
     * The live employee with this id in the bound tenant, or {@link NotFoundException}.
     *
     * <p>The single read path into {@code core.employee} from this package. A soft-deleted employee
     * is a {@code 404} and not a {@code 410}: to a caller that never saw it, it does not exist.
     */
    private Employee requireEmployee(UUID employeeId, UUID tenantId) {
        return employees
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .orElseThrow(() -> new NotFoundException(employeeId));
    }

    /**
     * Flushes now, so the unique index speaks while this method can still translate it.
     *
     * <p>Without the flush the violation surfaces at commit, outside every catch here, and reaches
     * the client as a {@code 500} carrying SQL and an index name.
     */
    private E save(E entity, UUID employeeId) {
        try {
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // Only the (tenant_id, employee_id) index means "someone else created this section".
            // Translating every integrity violation into that would repeat the defect W-13.1's review
            // found in EmployeeServiceImpl, where a foreign-key failure on tenant_id was reported to
            // the caller as a duplicate and the true cause was swallowed. Anything else is rethrown
            // unchanged.
            if (namesIndex(e, uniqueEmployeeIndexName())) {
                throw new ConcurrentWriteException(kind(), employeeId);
            }
            throw e;
        }
    }

    /** Runs {@link #validate} and turns anything it found into one {@link ValidationException}. */
    private void check(Q request) {
        if (request == null) {
            throw new ValidationException(Map.of("request", "A request body is required"));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        validate(request, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** Whether an index name appears anywhere in a throwable's cause chain. */
    private static boolean namesIndex(Throwable e, String indexName) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(indexName)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /**
     * The authenticated subject, for {@code created_by} / {@code updated_by}.
     *
     * <p>Falls back to {@code system} rather than failing: a migration or a scheduled job legitimately
     * has no principal. Who may call at all is settled by the filter chain long before here. Same
     * reasoning as {@code EmployeeServiceImpl.currentActor}.
     */
    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return EmployeeDetail.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }

    // ---- validation helpers, shared by the five subclasses ----------------------------------

    /**
     * A value that may be absent, checked against its column width.
     *
     * <p>Every width named by a subclass is the one in the migration. A value longer than the column
     * would otherwise be truncated or rejected by PostgreSQL at flush, as a {@code 500} naming a
     * constraint rather than a {@code 400} naming the field.
     */
    static String optional(Map<String, String> errors, String field, String value, int max) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > max) {
            errors.put(field, field + " must be at most " + max + " characters");
        }
        return trimmed;
    }

    /** A value that must be present, checked against its column width. */
    static String required(Map<String, String> errors, String field, String value, int max) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            errors.put(field, field + " is required");
            return null;
        }
        return optional(errors, field, trimmed, max);
    }

    /** {@link #optional}, and then the email shape if anything was given. */
    static String optionalEmail(Map<String, String> errors, String field, String value, int max) {
        String trimmed = optional(errors, field, value, max);
        if (trimmed != null && !errors.containsKey(field) && !trimmed.matches(EMAIL_PATTERN)) {
            errors.put(field, field + " is not an email address");
        }
        return trimmed;
    }

    /**
     * {@link #optional}, and then an exact shape if anything was given.
     *
     * @param pattern the regular expression the whole value must match
     * @param shape how the shape reads in an error message, for a user who has to fix it
     */
    static String optionalPattern(
            Map<String, String> errors, String field, String value, int max, String pattern, String shape) {
        String trimmed = optional(errors, field, value, max);
        if (trimmed != null && !errors.containsKey(field) && !trimmed.matches(pattern)) {
            errors.put(field, field + " must be " + shape);
        }
        return trimmed;
    }

    /**
     * The end of a shift must be after its start, when both are given.
     *
     * <p>Only checkable because the columns are {@code TIME} and the fields are {@link LocalTime}.
     * {@code Work.java:35-36} holds both as {@code String}, where this comparison has no meaning at
     * all — which is why a shift there can end before it starts and nothing notices.
     *
     * <p>Equal is refused too: a shift with no duration is a typo, not a night shift. An overnight
     * shift is not expressible in two {@code TIME} columns and is not in scope here.
     */
    static void checkShiftOrder(Map<String, String> errors, LocalTime start, LocalTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            errors.put("shiftEndTime", "shiftEndTime must be after shiftStartTime");
        }
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
