package com.infinevo.core.employee;

import com.infinevo.core.org.Department;
import com.infinevo.core.org.DepartmentRepository;
import com.infinevo.core.org.Designation;
import com.infinevo.core.org.DesignationRepository;
import com.infinevo.core.org.OrgMaster;
import com.infinevo.core.org.OrgMasterRepository;
import com.infinevo.core.org.WorkLocation;
import com.infinevo.core.org.WorkLocationRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every rule about an employee record lives here (W-13.1) — {@code docs/CONVENTIONS.md} section 3.
 *
 * <p>The tenant is read once per call from {@link TenantContext} and is never a parameter, never a
 * header, never a path variable. {@link TenantContext#require()} throws when none is bound, which is
 * the behaviour wanted: a query with no tenant is either a bug or a cross-tenant read.
 *
 * <p>Validation is hand-written rather than annotation-driven so that it is exercised by a plain
 * JUnit test with no Spring context, and so that it holds for a caller that is not an HTTP request.
 *
 * <p>Uniqueness of {@code employee_number} is checked here <em>and</em> enforced by the index at
 * {@code V010__employee.sql:47}. Both are needed: the check turns the ordinary case into a
 * {@code 409} with a sentence a user can act on, and the index closes the race between two
 * simultaneous creates that the check cannot. The second is caught below and reported the same way,
 * so neither path leaks a constraint-violation stack trace — spec section 9.
 */
@Service
public class EmployeeServiceImpl implements EmployeeService {

    /**
     * The unique index on {@code (tenant_id, employee_number)} — {@code V010__employee.sql:47}.
     * Named here so a constraint violation can be told apart from every other integrity failure.
     * If the index is ever renamed, rename it here too, or duplicates start surfacing as 500s.
     */
    private static final String EMPLOYEE_NUMBER_INDEX = "idx_employee_tenant_employee_number";

    private static final int MAX_EMPLOYEE_NUMBER = 64;
    private static final int MAX_NAME = 100;
    private static final int MAX_GENDER = 32;
    private static final int MAX_WORK_EMAIL = 255;
    private static final int MAX_MOBILE = 32;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final WorkLocationRepository workLocationRepository;

    public EmployeeServiceImpl(
            EmployeeRepository employeeRepository,
            DepartmentRepository departmentRepository,
            DesignationRepository designationRepository,
            WorkLocationRepository workLocationRepository) {
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.departmentRepository =
                Objects.requireNonNull(departmentRepository, "departmentRepository must not be null");
        this.designationRepository =
                Objects.requireNonNull(designationRepository, "designationRepository must not be null");
        this.workLocationRepository =
                Objects.requireNonNull(workLocationRepository, "workLocationRepository must not be null");
    }

    @Override
    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        UUID tenantId = TenantContext.require();
        Fields fields = validate(request, null);

        if (employeeRepository.existsByTenantIdAndEmployeeNumber(tenantId, fields.employeeNumber())) {
            throw new DuplicateEmployeeNumberException(fields.employeeNumber());
        }

        Employee employee = new Employee(tenantId, currentActor());
        fields.applyTo(employee, currentActor());
        assign(employee, request, tenantId);
        return EmployeeResponse.from(save(employee, fields.employeeNumber()));
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse get(UUID id) {
        return EmployeeResponse.from(require(id));
    }

    @Override
    @Transactional
    public EmployeeResponse update(UUID id, EmployeeRequest request) {
        Employee employee = require(id);
        Fields fields = validate(request, employee.getStatus());

        if (employeeRepository.existsByTenantIdAndEmployeeNumberAndIdNot(
                employee.getTenantId(), fields.employeeNumber(), employee.getId())) {
            throw new DuplicateEmployeeNumberException(fields.employeeNumber());
        }

        fields.applyTo(employee, currentActor());
        assign(employee, request, employee.getTenantId());
        return EmployeeResponse.from(save(employee, fields.employeeNumber()));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Employee employee = require(id);
        employee.markDeleted(currentActor());
        employeeRepository.save(employee);
    }

    /**
     * Resolves and assigns the three org masters — W-14.1, spec section 3 and section 7.
     *
     * <p><strong>This is the only thing stopping a cross-tenant assignment, and it has to be.</strong>
     * The columns carry foreign keys ({@code V014__employee_org_columns.sql}), but PostgreSQL runs
     * referential-integrity checks as the table owner with row security off, and the owner here is
     * {@code migration_user}. So the database will happily accept an employee in tenant A pointing at
     * a department in tenant B: the row exists, the key resolves, and no policy is consulted. Every id
     * is therefore looked up through {@code findByIdAndTenantId} in the bound tenant, and one that
     * does not resolve is a field error rather than a silent null — a request that names a department
     * and gets an employee with none would be the worst of the three outcomes.
     *
     * <p>Inactive masters are refused too, <em>unless the employee already holds that exact one</em>.
     * That is what "deactivating hides a value from new assignments without breaking the employees who
     * hold it" means in practice (spec section 4): the employee can be updated, renamed and
     * terminated while keeping a retired department, and cannot be moved into one.
     */
    private void assign(Employee employee, EmployeeRequest request, UUID tenantId) {
        Map<String, String> errors = new LinkedHashMap<>();
        Department department = resolve(
                departmentRepository,
                request.departmentId(),
                tenantId,
                "departmentId",
                "department",
                employee.getDepartment(),
                errors);
        Designation designation = resolve(
                designationRepository,
                request.designationId(),
                tenantId,
                "designationId",
                "designation",
                employee.getDesignation(),
                errors);
        WorkLocation workLocation = resolve(
                workLocationRepository,
                request.workLocationId(),
                tenantId,
                "workLocationId",
                "work location",
                employee.getWorkLocation(),
                errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        employee.assign(department, designation, workLocation);
    }

    /**
     * One org master, looked up inside the bound tenant.
     *
     * @param current what the employee holds now, so a record that has since been deactivated is not
     *     torn off an employee who legitimately holds it
     * @return the record, or null when the request named none
     */
    private <T extends OrgMaster> T resolve(
            OrgMasterRepository<T> repository,
            UUID id,
            UUID tenantId,
            String field,
            String kind,
            OrgMaster current,
            Map<String, String> errors) {
        if (id == null) {
            return null;
        }
        Optional<T> found = repository.findByIdAndTenantId(id, tenantId);
        if (found.isEmpty()) {
            // Deliberately the same message whether the record does not exist at all or belongs to
            // another tenant. Telling the two apart would let a caller enumerate another tenant's ids.
            errors.put(field, "No " + kind + " " + id + " in this tenant");
            return null;
        }
        T master = found.get();
        if (!master.isActive() && (current == null || !id.equals(current.getId()))) {
            errors.put(field, "The " + kind + " " + master.getCode() + " is inactive and cannot be assigned");
        }
        return master;
    }

    /**
     * The live employee with this id in the bound tenant, or {@link NotFoundException}.
     *
     * <p>The single read path in this class, so "soft-deleted rows are invisible" is one line rather
     * than a predicate each method has to remember. A soft-deleted employee is a {@code 404} and not
     * a {@code 410}: to a caller that never saw it, it does not exist.
     */
    private Employee require(UUID id) {
        UUID tenantId = TenantContext.require();
        return employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(id, tenantId)
                .orElseThrow(() -> new NotFoundException(id));
    }

    /**
     * Flushes now, so the unique index speaks while this method can still translate it.
     *
     * <p>Without the flush the violation surfaces at commit, outside every catch here, and reaches
     * the client as a {@code 500} carrying SQL.
     */
    private Employee save(Employee employee, String employeeNumber) {
        try {
            return employeeRepository.saveAndFlush(employee);
        } catch (DataIntegrityViolationException e) {
            // Only the employee-number index means "duplicate". This catch used to translate every
            // integrity violation into DuplicateEmployeeNumberException, which meant a foreign-key
            // failure on tenant_id — a token naming a tenant with no core.tenant row, which
            // V010__employee.sql:6 makes possible — was reported to the caller as "employee number
            // EMP-0001 is already in use in this tenant". The real cause was swallowed and the
            // message was false. Anything that is not this index is rethrown unchanged.
            if (namesEmployeeNumberIndex(e)) {
                throw new DuplicateEmployeeNumberException(employeeNumber);
            }
            throw e;
        }
    }

    /** The unique index from {@code V010__employee.sql:47}, by name, anywhere in the cause chain. */
    private static boolean namesEmployeeNumberIndex(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message != null && message.contains(EMPLOYEE_NUMBER_INDEX)) {
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
     * has no principal, and refusing to write an employee because nobody is logged in would be a
     * strange way to enforce an audit column. Who may call at all is settled by the filter chain long
     * before here.
     */
    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return Employee.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    /**
     * Checks one request and returns the cleaned values.
     *
     * @param currentStatus the status the row holds now, or null when creating. A status transition
     *     is only meaningful against something.
     */
    private static Fields validate(EmployeeRequest request, EmploymentStatus currentStatus) {
        if (request == null) {
            throw new ValidationException(Map.of("request", "A request body is required"));
        }
        Map<String, String> errors = new LinkedHashMap<>();

        String employeeNumber = required(errors, "employeeNumber", request.employeeNumber(), MAX_EMPLOYEE_NUMBER);
        String firstName = required(errors, "firstName", request.firstName(), MAX_NAME);
        String middleName = optional(errors, "middleName", request.middleName(), MAX_NAME);
        String lastName = optional(errors, "lastName", request.lastName(), MAX_NAME);
        String gender = optional(errors, "gender", request.gender(), MAX_GENDER);
        String workEmail = optional(errors, "workEmail", request.workEmail(), MAX_WORK_EMAIL);
        String mobile = optional(errors, "mobile", request.mobile(), MAX_MOBILE);

        if (workEmail != null && !workEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            errors.put("workEmail", "workEmail is not an email address");
        }

        LocalDate dateOfJoining = request.dateOfJoining();
        if (dateOfJoining == null) {
            errors.put("dateOfJoining", "dateOfJoining is required");
        }

        // A null status on update means "leave it where it is", so a client that omits the field
        // cannot silently revive a terminated employee. On create there is nothing to leave it at,
        // and ACTIVE is what creating an employee means.
        EmploymentStatus status = request.status() != null
                ? request.status()
                : (currentStatus != null ? currentStatus : EmploymentStatus.ACTIVE);

        if (currentStatus != null && !currentStatus.canTransitionTo(status)) {
            errors.put("status", "An employee cannot go from " + currentStatus + " to " + status);
        }

        LocalDate terminationDate = request.terminationDate();
        if (status.requiresTerminationDate() && terminationDate == null) {
            errors.put("terminationDate", "terminationDate is required when status is " + status);
        }
        if (!status.requiresTerminationDate() && terminationDate != null) {
            errors.put("terminationDate", "terminationDate may only be set when status is TERMINATED");
        }
        if (terminationDate != null && dateOfJoining != null && terminationDate.isBefore(dateOfJoining)) {
            errors.put("terminationDate", "terminationDate cannot precede dateOfJoining");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Fields(
                employeeNumber,
                firstName,
                middleName,
                lastName,
                gender,
                dateOfJoining,
                terminationDate,
                status,
                workEmail,
                mobile,
                request.portalEnabled() == null || request.portalEnabled());
    }

    private static String required(Map<String, String> errors, String field, String value, int max) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            errors.put(field, field + " is required");
            return null;
        }
        return lengthChecked(errors, field, trimmed, max);
    }

    private static String optional(Map<String, String> errors, String field, String value, int max) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : lengthChecked(errors, field, trimmed, max);
    }

    private static String lengthChecked(Map<String, String> errors, String field, String value, int max) {
        if (value.length() > max) {
            errors.put(field, field + " must be at most " + max + " characters");
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The validated, cleaned request. Exists so {@link #validate} returns values rather than mutating. */
    private record Fields(
            String employeeNumber,
            String firstName,
            String middleName,
            String lastName,
            String gender,
            LocalDate dateOfJoining,
            LocalDate terminationDate,
            EmploymentStatus status,
            String workEmail,
            String mobile,
            boolean portalEnabled) {

        void applyTo(Employee employee, String actor) {
            employee.apply(
                    employeeNumber,
                    firstName,
                    middleName,
                    lastName,
                    gender,
                    dateOfJoining,
                    terminationDate,
                    status,
                    workEmail,
                    mobile,
                    portalEnabled,
                    actor);
        }
    }
}
