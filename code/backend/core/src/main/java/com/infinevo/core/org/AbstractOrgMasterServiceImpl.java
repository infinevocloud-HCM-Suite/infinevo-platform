package com.infinevo.core.org;

import com.infinevo.shared.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every rule the three org masters share (W-14.1) — {@code docs/CONVENTIONS.md} section 3.
 *
 * <p>Two of the three masters are identical and the third differs by an address and a flag, so this
 * holds the create / list / update / delete flow once and the three subclasses supply what actually
 * differs: the entity, the response, the unique index's name and the "is anyone assigned to this"
 * count. That is the whole of the abstraction — there is no registry, no reflection and no
 * configuration. A fourth master would extend this; a master with different rules would not, and
 * should not be forced to.
 *
 * <p>The tenant is read once per call from {@link TenantContext} and is never a parameter, never a
 * header, never a path variable. {@link TenantContext#require()} throws when none is bound, which is
 * the behaviour wanted: a query with no tenant is either a bug or a cross-tenant read.
 *
 * <p>Uniqueness of {@code code} is checked here <em>and</em> enforced by the index in each migration.
 * Both are needed: the check turns the ordinary case into a {@code 409} with a sentence a user can
 * act on, and the index closes the race between two simultaneous creates that the check cannot.
 *
 * @param <E> the entity this service owns
 * @param <Q> its request type
 * @param <R> its response type
 */
abstract class AbstractOrgMasterServiceImpl<E extends OrgMaster, Q extends OrgMasterRequest, R>
        implements OrgMasterService<Q, R> {

    static final int MAX_CODE = 32;
    static final int MAX_NAME = 128;

    /** The audit columns are {@code varchar(100)} in every migration. */
    private static final int MAX_ACTOR = 100;

    private final OrgMasterRepository<E> repository;

    protected AbstractOrgMasterServiceImpl(OrgMasterRepository<E> repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /** The word this master is called in an error message — "department", "designation", "work location". */
    protected abstract String kind();

    /** The name of the {@code (tenant_id, code)} unique index, so its violation can be told from every other. */
    protected abstract String uniqueCodeIndexName();

    /** A new, unsaved entity for this tenant. */
    protected abstract E newEntity(UUID tenantId, String actor);

    /** Repacks a persisted entity into this master's response. */
    protected abstract R toResponse(E entity);

    /**
     * How many employees in this tenant point at this record — including soft-deleted ones.
     *
     * <p>Deleted employees count on purpose. The row still holds the foreign key, so deleting the
     * master would fail in the database anyway; and a past pay run that named the department is
     * history somebody may have to reproduce.
     */
    protected abstract long countAssigned(UUID tenantId, UUID id);

    /** Checks the fields only this master has. Adds to {@code errors}; throws nothing. */
    protected void validateExtra(Q request, Map<String, String> errors) {
        // Department and designation have none.
    }

    /** Copies the fields only this master has onto the entity, after validation. */
    protected void applyExtra(E entity, Q request) {
        // Department and designation have none.
    }

    /**
     * A rule that needs its own query, checked before the entity is touched. Where the filing-address
     * rule lives.
     *
     * <p>Before, and not after, on purpose. Hibernate auto-flushes a dirty persistence context ahead
     * of a query against the same table, so a check that ran after {@link OrgMaster#apply} would push
     * the half-applied row into the database first — and the partial unique index would then answer
     * with a constraint violation from inside the repository call, where nothing can turn it back
     * into a sentence.
     *
     * @param id the row being updated, or null on a create
     */
    protected void checkTenantInvariants(Q request, UUID id, UUID tenantId) {
        // Department and designation have no such rule.
    }

    /**
     * Turns an integrity violation that is not the code index into the exception it deserves.
     *
     * <p>Default: the violation as it stands. Overriding to report something as a duplicate without
     * checking which index fired is the defect this signature exists to prevent — see {@link #save}.
     */
    protected RuntimeException translateIntegrityViolation(DataIntegrityViolationException e) {
        return e;
    }

    @Override
    @Transactional
    public R create(Q request) {
        UUID tenantId = TenantContext.require();
        Cleaned cleaned = validate(request, null);

        if (repository.existsByTenantIdAndCode(tenantId, cleaned.code())) {
            throw new DuplicateCodeException(kind(), cleaned.code());
        }

        checkTenantInvariants(request, null, tenantId);

        String actor = currentActor();
        E entity = newEntity(tenantId, actor);
        entity.apply(cleaned.code(), cleaned.name(), cleaned.active(), actor);
        applyExtra(entity, request);
        return toResponse(save(entity, cleaned.code()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<R> list(boolean activeOnly) {
        UUID tenantId = TenantContext.require();
        List<E> rows = activeOnly
                ? repository.findByTenantIdAndActiveTrueOrderByCodeAsc(tenantId)
                : repository.findByTenantIdOrderByCodeAsc(tenantId);
        return rows.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public R update(UUID id, Q request) {
        UUID tenantId = TenantContext.require();
        E entity = require(id, tenantId);
        Cleaned cleaned = validate(request, entity.isActive());

        if (repository.existsByTenantIdAndCodeAndIdNot(tenantId, cleaned.code(), entity.getId())) {
            throw new DuplicateCodeException(kind(), cleaned.code());
        }

        checkTenantInvariants(request, entity.getId(), tenantId);

        entity.apply(cleaned.code(), cleaned.name(), cleaned.active(), currentActor());
        applyExtra(entity, request);
        return toResponse(save(entity, cleaned.code()));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        UUID tenantId = TenantContext.require();
        E entity = require(id, tenantId);

        long assigned = countAssigned(tenantId, id);
        if (assigned > 0) {
            throw new RecordInUseException(kind(), assigned);
        }

        // Flushed here for the same reason save() flushes: without it a foreign-key violation
        // surfaces at commit, outside every catch in this class, and reaches the caller as a 500
        // carrying SQL. The count above should have caught it — but the count and the constraint
        // are two separate reads of the same truth, and they can disagree under a concurrent
        // assignment. When they do, the caller should still get the sentence, not the stack trace.
        try {
            repository.delete(entity);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            // assigned is 0 here by definition — the check above passed — so it cannot be used in
            // the message. "in use by 1" is the honest minimum: the constraint proves at least one.
            throw new RecordInUseException(kind(), 1);
        }
    }

    /** The record with this id in the bound tenant, or {@link NotFoundException}. The single read path. */
    private E require(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new NotFoundException(kind(), id));
    }

    /**
     * Flushes now, so the unique index speaks while this method can still translate it.
     *
     * <p>Without the flush the violation surfaces at commit, outside every catch here, and reaches
     * the client as a {@code 500} carrying SQL.
     */
    private E save(E entity, String code) {
        try {
            return repository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // Only the (tenant_id, code) index means "duplicate code". Catching every integrity
            // violation and calling it a duplicate is a real defect and was a review finding on
            // W-13.1's EmployeeServiceImpl: a foreign-key failure on tenant_id then reported
            // "code FIN already exists in this tenant", the true cause was swallowed and the
            // message was false. Anything else goes to translateIntegrityViolation, which by
            // default rethrows it unchanged.
            if (namesIndex(e, uniqueCodeIndexName())) {
                throw new DuplicateCodeException(kind(), code);
            }
            throw translateIntegrityViolation(e);
        }
    }

    /** Whether an index name appears anywhere in a throwable's cause chain. */
    static boolean namesIndex(Throwable e, String indexName) {
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
            return OrgMaster.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_ACTOR ? name.substring(0, MAX_ACTOR) : name;
    }

    /**
     * Checks one request and returns the cleaned common values.
     *
     * @param currentActive the flag the row holds now, or null when creating. Null {@code active} on
     *     an update means "leave it where it is"; on a create there is nothing to leave it at, and a
     *     master is created available for use.
     */
    private Cleaned validate(Q request, Boolean currentActive) {
        if (request == null) {
            throw new ValidationException(Map.of("request", "A request body is required"));
        }
        Map<String, String> errors = new LinkedHashMap<>();

        String code = required(errors, "code", request.code(), MAX_CODE);
        String name = required(errors, "name", request.name(), MAX_NAME);
        validateExtra(request, errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        boolean active = request.active() != null ? request.active() : (currentActive == null || currentActive);
        return new Cleaned(code, name, active);
    }

    static String required(Map<String, String> errors, String field, String value, int max) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            errors.put(field, field + " is required");
            return null;
        }
        return lengthChecked(errors, field, trimmed, max);
    }

    static String optional(Map<String, String> errors, String field, String value, int max) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : lengthChecked(errors, field, trimmed, max);
    }

    private static String lengthChecked(Map<String, String> errors, String field, String value, int max) {
        if (value.length() > max) {
            errors.put(field, field + " must be at most " + max + " characters");
        }
        return value;
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The validated, cleaned common fields. Exists so {@code validate} returns values rather than mutating. */
    private record Cleaned(String code, String name, boolean active) {}
}
