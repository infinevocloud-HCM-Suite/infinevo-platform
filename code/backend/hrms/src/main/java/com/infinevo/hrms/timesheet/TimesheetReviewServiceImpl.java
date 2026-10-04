package com.infinevo.hrms.timesheet;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.hrms.project.ResourceNotFoundException;
import com.infinevo.hrms.project.ValidationException;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link TimesheetReviewService} (W-42.4).
 *
 * <p>One week, one row: a page of week ids comes first, then the weeks for those ids in a single query that brings
 * their project lines with them; tasks and days load in batches, one query per level. Nothing is loaded per row, as
 * legacy did (TimesheetServiceImpl.java:436-444).
 */
@Service
@Transactional(readOnly = true)
public class TimesheetReviewServiceImpl implements TimesheetReviewService {

    static final int MAX_PAGE_SIZE = 100;
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);
    private static final Set<TimesheetStatus> REVIEWABLE =
            Set.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED, TimesheetStatus.REJECTED);
    /** A value to bind where a filter is off: the flag next to it says it is not used. */
    private static final UUID UNUSED = new UUID(0L, 0L);

    private final TimesheetRepository timesheetRepository;
    private final TimesheetAccessResolver accessResolver;
    private final EmployeeService employeeService;
    private final TimesheetNames names;

    public TimesheetReviewServiceImpl(
            TimesheetRepository timesheetRepository,
            TimesheetAccessResolver accessResolver,
            EmployeeService employeeService,
            TimesheetNames names) {
        this.names = Objects.requireNonNull(names, "names must not be null");
        this.timesheetRepository = Objects.requireNonNull(timesheetRepository, "timesheetRepository must not be null");
        this.accessResolver = Objects.requireNonNull(accessResolver, "accessResolver must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    public TimesheetPage managed(
            LocalDate from, LocalDate to, TimesheetStatus status, UUID projectId, int page, int size) {
        UUID tenantId = TenantContext.require();
        UUID me = caller(TimesheetAccessResolver.ACTION_APPROVE);
        Filters f = Filters.of(from, to, status, page, size);

        Set<UUID> managed = accessResolver.managedProjectIds(tenantId, me);
        Set<UUID> projects = managed;
        if (projectId != null) {
            if (!managed.contains(projectId)) {
                throw new ValidationException("projectId", "You do not manage that project");
            }
            projects = Set.of(projectId);
        }
        if (projects.isEmpty()) {
            return TimesheetPage.empty(f.page, f.size);
        }
        Page<UUID> ids = timesheetRepository.pageOnProjects(tenantId, projects, f.statuses, f.from, f.to, f.pageable());
        // Trimmed to the manager's projects: a colleague's hours on other projects are not theirs to see.
        Set<UUID> visible = projects;
        return render(tenantId, ids, (sheet, n) -> TimesheetResponse.from(sheet, visible, n));
    }

    @Override
    public TimesheetPage team(
            LocalDate from, LocalDate to, TimesheetStatus status, UUID employeeId, int page, int size) {
        UUID tenantId = TenantContext.require();
        UUID me = caller(TimesheetAccessResolver.ACTION_READ_TEAM);
        Filters f = Filters.of(from, to, status, page, size);

        Set<UUID> reports = accessResolver.directReportIds(tenantId, me);
        Set<UUID> employees = reports;
        if (employeeId != null) {
            if (!reports.contains(employeeId)) {
                throw new ValidationException("employeeId", "That employee is not one of your direct reports");
            }
            employees = Set.of(employeeId);
        }
        if (employees.isEmpty()) {
            return TimesheetPage.empty(f.page, f.size);
        }
        Page<UUID> ids =
                timesheetRepository.pageForEmployees(tenantId, employees, f.statuses, f.from, f.to, f.pageable());
        return render(tenantId, ids, (sheet, n) -> TimesheetResponse.from(sheet, n));
    }

    @Override
    public TimesheetPage all(
            LocalDate from, LocalDate to, TimesheetStatus status, UUID employeeId, UUID projectId, int page, int size) {
        UUID tenantId = TenantContext.require();
        caller(TimesheetAccessResolver.ACTION_READ);
        Filters f = Filters.of(from, to, status, page, size);

        Page<UUID> ids = timesheetRepository.pageAll(
                tenantId,
                f.statuses,
                f.from,
                f.to,
                employeeId == null,
                employeeId != null ? employeeId : UNUSED,
                projectId == null,
                projectId != null ? projectId : UNUSED,
                f.pageable());
        return render(tenantId, ids, (sheet, n) -> TimesheetResponse.from(sheet, n));
    }

    @Override
    public TimesheetResponse get(UUID id) {
        UUID tenantId = TenantContext.require();
        UUID me = caller(TimesheetAccessResolver.ACTION_READ_OWN);
        Timesheet sheet = timesheetRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("No timesheet " + id));
        TimesheetAccessResolver.Access access = accessResolver.resolve(tenantId, me, sheet);
        return switch (access.scope()) {
            case FULL -> TimesheetResponse.from(sheet, names.forSheets(tenantId, List.of(sheet)));
            case PARTIAL ->
                TimesheetResponse.from(sheet, access.projectIds(), names.forSheets(tenantId, List.of(sheet)));
            // Not there and not yours answer alike, so a probe cannot tell which ids exist.
            case NONE -> throw new ResourceNotFoundException("No timesheet " + id);
        };
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    /** The login's employee; a login with no employee record is the permission error (403), never a 500. */
    private UUID caller(String action) {
        return employeeService
                .currentEmployee()
                .map(EmployeeResponse::id)
                .orElseThrow(() -> new PermissionDeniedException(action));
    }

    /** Loads the weeks of a page of ids, in that page's order, reads their names in one batch, and maps each. */
    private TimesheetPage render(
            UUID tenantId, Page<UUID> ids, BiFunction<Timesheet, TimesheetNames.Names, TimesheetResponse> mapper) {
        if (ids.isEmpty()) {
            return TimesheetPage.of(List.of(), ids);
        }
        Map<UUID, Timesheet> byId = new HashMap<>();
        timesheetRepository.findAllWithProjects(tenantId, ids.getContent()).forEach(t -> byId.put(t.getId(), t));
        TimesheetNames.Names pageNames = names.forSheets(tenantId, byId.values());
        List<TimesheetResponse> content = ids.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(sheet -> mapper.apply(sheet, pageNames))
                .collect(Collectors.toList());
        return TimesheetPage.of(content, ids);
    }

    /** The filters every list shares, checked once. */
    private record Filters(LocalDate from, LocalDate to, Set<TimesheetStatus> statuses, int page, int size) {

        static Filters of(LocalDate from, LocalDate to, TimesheetStatus status, int page, int size) {
            Map<String, String> errors = new HashMap<>();
            if (status != null && !REVIEWABLE.contains(status)) {
                errors.put("status", "status must be SUBMITTED, APPROVED or REJECTED; a draft is its owner's alone");
            }
            LocalDate lower = from != null ? from : EARLIEST;
            LocalDate upper = to != null ? to : LATEST;
            if (lower.isAfter(upper)) {
                errors.put("from", "from must not be after to");
            }
            if (page < 0) {
                errors.put("page", "page must not be negative");
            }
            if (size < 1) {
                errors.put("size", "size must be at least 1");
            }
            if (!errors.isEmpty()) {
                throw new ValidationException(errors);
            }
            return new Filters(
                    lower, upper, status != null ? Set.of(status) : REVIEWABLE, page, Math.min(size, MAX_PAGE_SIZE));
        }

        PageRequest pageable() {
            return PageRequest.of(page, size);
        }
    }
}
