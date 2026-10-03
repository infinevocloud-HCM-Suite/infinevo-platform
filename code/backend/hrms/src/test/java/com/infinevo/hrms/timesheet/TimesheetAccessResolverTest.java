package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.hrms.project.Project;
import com.infinevo.hrms.project.ProjectRepository;
import com.infinevo.hrms.timesheet.TimesheetAccessResolver.Access;
import com.infinevo.hrms.timesheet.TimesheetAccessResolver.Scope;
import com.infinevo.shared.authz.PermissionService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.4 §7, {@code TimesheetAccessResolverTest}: for a caller and a week, none, part or all of it.
 */
class TimesheetAccessResolverTest {

    private static final LocalDate WEEK = LocalDate.of(2026, 10, 5);

    private final UUID tenant = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final UUID caller = UUID.randomUUID();
    private final UUID projectA = UUID.randomUUID();
    private final UUID projectB = UUID.randomUUID();

    private PermissionService permissions;
    private ReportingLineRepository lines;
    private ProjectRepository projects;
    private TimesheetAccessResolver resolver;
    private final Set<String> held = new HashSet<>();

    @BeforeEach
    void setUp() {
        permissions = mock(PermissionService.class);
        when(permissions.holds(any())).thenAnswer(i -> held.contains(i.<String>getArgument(0)));
        lines = mock(ReportingLineRepository.class);
        projects = mock(ProjectRepository.class);
        resolver = new TimesheetAccessResolver(permissions, lines, projects);
    }

    private Timesheet week(UUID employee, TimesheetStatus status, UUID... projectIds) {
        Timesheet sheet = new Timesheet(tenant, employee, WEEK, "test");
        sheet.setStatus(status);
        for (UUID projectId : projectIds) {
            TimesheetProjectEntry entry = sheet.addProject(projectId, "test");
            entry.setStatus(status);
        }
        return sheet;
    }

    private void directReport(UUID reportId) {
        Employee report = mock(Employee.class);
        when(report.getId()).thenReturn(reportId);
        ReportingLine line = mock(ReportingLine.class);
        when(line.getEmployee()).thenReturn(report);
        when(lines.findDirectReports(eq(tenant), eq(caller), any())).thenReturn(List.of(line));
    }

    private void manages(UUID... projectIds) {
        List<Project> managed = java.util.Arrays.stream(projectIds)
                .map(id -> {
                    Project p = mock(Project.class);
                    when(p.getId()).thenReturn(id);
                    return p;
                })
                .toList();
        when(projects.searchProjects(eq(tenant), any(), anyBoolean(), eq(caller), any()))
                .thenReturn(managed);
    }

    @Test
    @DisplayName("The owner sees a draft of their own, whole")
    void ownerSeesTheirDraft() {
        held.add("hrms.timesheet.read_own");

        Access access = resolver.resolve(tenant, owner, week(owner, TimesheetStatus.DRAFT, projectA));

        assertThat(access.scope()).isEqualTo(Scope.FULL);
    }

    @Test
    @DisplayName("Nobody but the owner sees a draft: not HR, not the reporting manager, not the project manager")
    void nobodyElseSeesADraft() {
        held.addAll(Set.of("hrms.timesheet.read", "hrms.timesheet.read_team", "hrms.timesheet.approve"));
        directReport(owner);
        manages(projectA);

        assertThat(resolver.resolve(tenant, caller, week(owner, TimesheetStatus.DRAFT, projectA))
                        .scope())
                .isEqualTo(Scope.NONE);
        assertThat(resolver.resolve(tenant, caller, week(owner, TimesheetStatus.CANCELLED, projectA))
                        .scope())
                .isEqualTo(Scope.NONE);
    }

    @Test
    @DisplayName("HR sees all of a submitted week, and all of one in any other status past draft")
    void hrSeesAllOfASubmittedWeek() {
        held.add("hrms.timesheet.read");

        for (TimesheetStatus status :
                List.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED, TimesheetStatus.REJECTED)) {
            assertThat(resolver.resolve(tenant, caller, week(owner, status, projectA, projectB))
                            .scope())
                    .as(status.name())
                    .isEqualTo(Scope.FULL);
        }
    }

    @Test
    @DisplayName("The reporting manager sees all of a direct report's week and none of an indirect report's")
    void reportingManagerSeesDirectReportsOnly() {
        held.add("hrms.timesheet.read_team");
        UUID indirect = UUID.randomUUID();
        directReport(owner);

        assertThat(resolver.resolve(tenant, caller, week(owner, TimesheetStatus.SUBMITTED, projectA, projectB))
                        .scope())
                .isEqualTo(Scope.FULL);
        assertThat(resolver.resolve(tenant, caller, week(indirect, TimesheetStatus.SUBMITTED, projectA))
                        .scope())
                .isEqualTo(Scope.NONE);
    }

    @Test
    @DisplayName("A project manager sees only their project's line of a two-project week")
    void projectManagerSeesOnlyTheirLine() {
        held.add("hrms.timesheet.approve");
        manages(projectA);

        Access access = resolver.resolve(tenant, caller, week(owner, TimesheetStatus.SUBMITTED, projectA, projectB));

        assertThat(access.scope()).isEqualTo(Scope.PARTIAL);
        assertThat(access.projectIds()).containsExactly(projectA);
    }

    @Test
    @DisplayName("A project manager of neither project, and a caller with no action, see nothing")
    void anUnrelatedManagerSeesNothing() {
        held.add("hrms.timesheet.approve");
        manages(UUID.randomUUID());

        assertThat(resolver.resolve(tenant, caller, week(owner, TimesheetStatus.SUBMITTED, projectA, projectB))
                        .sees())
                .isFalse();

        held.clear();
        manages(projectA);
        assertThat(resolver.resolve(tenant, caller, week(owner, TimesheetStatus.SUBMITTED, projectA))
                        .sees())
                .isFalse();
    }

    @Test
    @DisplayName("Holding approve is not enough to read one's own week: that is read_own, and a draft stays closed")
    void ownerWithoutReadOwnDoesNotSeeTheirDraft() {
        held.add("hrms.timesheet.approve");
        manages(projectA);

        assertThat(resolver.resolve(tenant, owner, week(owner, TimesheetStatus.DRAFT, projectA))
                        .scope())
                .isEqualTo(Scope.NONE);
    }
}
