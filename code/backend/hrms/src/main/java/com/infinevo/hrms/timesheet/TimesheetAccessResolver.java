package com.infinevo.hrms.timesheet;

import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.hrms.project.Project;
import com.infinevo.hrms.project.ProjectRepository;
import com.infinevo.hrms.project.ProjectStatus;
import com.infinevo.shared.authz.PermissionService;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Who sees how much of a timesheet (W-42.4 §4). One place decides, for a caller and a week, whether they see none,
 * part or all of it, so the lists and {@code GET /{id}} cannot drift apart. The shape follows
 * {@code ProjectAccessResolver}.
 *
 * <ol>
 *   <li>The owner, holding {@code hrms.timesheet.read_own}, sees all of their own week, a draft included.
 *   <li>Nobody else sees a draft (or a cancelled week).
 *   <li>{@code hrms.timesheet.read} (HR) sees all of any other week.
 *   <li>{@code hrms.timesheet.read_team} sees all of a direct report's week, and none of an indirect report's.
 *   <li>{@code hrms.timesheet.approve} sees, of a week that touches a project they manage, only the lines on those
 *       projects. Colleagues' hours on other projects are not theirs to see (legacy
 *       {@code TimesheetServiceImpl.java:433-443}).
 * </ol>
 */
@Component
public class TimesheetAccessResolver {

    public static final String ACTION_READ_OWN = "hrms.timesheet.read_own";
    public static final String ACTION_READ_TEAM = "hrms.timesheet.read_team";
    public static final String ACTION_READ = "hrms.timesheet.read";
    public static final String ACTION_APPROVE = "hrms.timesheet.approve";

    /** How much of a week a caller sees. */
    public enum Scope {
        NONE,
        PARTIAL,
        FULL
    }

    /**
     * The answer: a scope and, for {@link Scope#PARTIAL}, the projects whose lines the caller may see.
     *
     * @param projectIds empty unless {@code scope} is {@code PARTIAL}
     */
    public record Access(Scope scope, Set<UUID> projectIds) {

        static final Access NONE = new Access(Scope.NONE, Set.of());
        static final Access FULL = new Access(Scope.FULL, Set.of());

        static Access partial(Set<UUID> projectIds) {
            return new Access(Scope.PARTIAL, Set.copyOf(projectIds));
        }

        public boolean sees() {
            return scope != Scope.NONE;
        }
    }

    private final PermissionService permissionService;
    private final ReportingLineRepository reportingLineRepository;
    private final ProjectRepository projectRepository;

    public TimesheetAccessResolver(
            PermissionService permissionService,
            ReportingLineRepository reportingLineRepository,
            ProjectRepository projectRepository) {
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.reportingLineRepository =
                Objects.requireNonNull(reportingLineRepository, "reportingLineRepository must not be null");
        this.projectRepository = Objects.requireNonNull(projectRepository, "projectRepository must not be null");
    }

    /** What {@code callerEmployeeId} sees of {@code sheet}. Call inside the transaction that loaded the week. */
    public Access resolve(UUID tenantId, UUID callerEmployeeId, Timesheet sheet) {
        boolean owner = callerEmployeeId.equals(sheet.getEmployeeId());
        if (owner && permissionService.holds(ACTION_READ_OWN)) {
            return Access.FULL;
        }
        if (sheet.getStatus() == TimesheetStatus.DRAFT || sheet.getStatus() == TimesheetStatus.CANCELLED) {
            return Access.NONE;
        }
        if (permissionService.holds(ACTION_READ)) {
            return Access.FULL;
        }
        if (permissionService.holds(ACTION_READ_TEAM)
                && directReportIds(tenantId, callerEmployeeId).contains(sheet.getEmployeeId())) {
            return Access.FULL;
        }
        if (permissionService.holds(ACTION_APPROVE)) {
            Set<UUID> managed = managedProjectIds(tenantId, callerEmployeeId);
            Set<UUID> theirs = sheet.getProjects().stream()
                    .filter(p -> p.getStatus() != TimesheetStatus.DRAFT)
                    .map(TimesheetProjectEntry::getProjectId)
                    .filter(managed::contains)
                    .collect(Collectors.toCollection(HashSet::new));
            if (!theirs.isEmpty()) {
                return Access.partial(theirs);
            }
        }
        return Access.NONE;
    }

    /** The caller's direct reports today: the primary line only, as legacy. */
    public Set<UUID> directReportIds(UUID tenantId, UUID managerEmployeeId) {
        return reportingLineRepository.findDirectReports(tenantId, managerEmployeeId, LocalDate.now()).stream()
                .map(ReportingLine::getEmployee)
                .map(e -> e.getId())
                .collect(Collectors.toSet());
    }

    /** The projects the caller is manager of. Every status is passed: the query binds no nulls. */
    public Set<UUID> managedProjectIds(UUID tenantId, UUID managerEmployeeId) {
        return projectRepository
                .searchProjects(tenantId, EnumSet.allOf(ProjectStatus.class), false, managerEmployeeId, "%")
                .stream()
                .map(Project::getId)
                .collect(Collectors.toSet());
    }
}
