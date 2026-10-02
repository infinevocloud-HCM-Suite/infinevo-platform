package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApproverKind;
import com.infinevo.core.employee.Employee;
import com.infinevo.core.org.ReportingLineService;
import com.infinevo.hrms.project.Project;
import com.infinevo.hrms.project.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.3 §7, {@code TimesheetProjectManagerResolverTest}: the project's manager, with the founder's two exceptions.
 */
class TimesheetProjectManagerResolverTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID employee = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();

    private ProjectRepository projects;
    private ReportingLineService reportingLines;
    private TimesheetProjectManagerResolver resolver;

    @BeforeEach
    void setUp() {
        projects = mock(ProjectRepository.class);
        reportingLines = mock(ReportingLineService.class);
        resolver = new TimesheetProjectManagerResolver(projects, reportingLines);
    }

    private void projectManagedBy(UUID managerId) {
        Project project = mock(Project.class);
        when(project.getManagerEmployeeId()).thenReturn(managerId);
        when(projects.findByIdAndTenantIdAndDeletedFalse(projectId, tenant)).thenReturn(Optional.of(project));
    }

    @Test
    @DisplayName("It answers for PROJECT_MANAGER, and is what core finds for that kind")
    void kindIsProjectManager() {
        assertThat(resolver.kind()).isEqualTo(ApproverKind.PROJECT_MANAGER);
    }

    @Test
    @DisplayName("The project's manager is returned")
    void managerIsReturned() {
        projectManagedBy(manager);

        assertThat(resolver.resolve(tenant, employee, projectId.toString())).contains(manager);
    }

    @Test
    @DisplayName("A project with no manager resolves to nobody, so an administrator decides the step")
    void noManagerGivesEmpty() {
        projectManagedBy(null);

        assertThat(resolver.resolve(tenant, employee, projectId.toString())).isEmpty();
    }

    @Test
    @DisplayName("A deleted or unknown project resolves to nobody")
    void deletedProjectGivesEmpty() {
        when(projects.findByIdAndTenantIdAndDeletedFalse(projectId, tenant)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(tenant, employee, projectId.toString())).isEmpty();
    }

    @Test
    @DisplayName("When the manager is the employee themself, their reporting manager is returned")
    void selfManagedGivesTheReportingManager() {
        projectManagedBy(employee);
        UUID boss = UUID.randomUUID();
        Employee reportingManager = mock(Employee.class);
        when(reportingManager.getId()).thenReturn(boss);
        when(reportingLines.chainAbove(eq(employee), any())).thenReturn(List.of(reportingManager));

        assertThat(resolver.resolve(tenant, employee, projectId.toString())).contains(boss);
    }

    @Test
    @DisplayName("When the manager is the employee themself and there is no reporting line, nobody")
    void selfManagedWithNoReportingLineGivesEmpty() {
        projectManagedBy(employee);
        when(reportingLines.chainAbove(eq(employee), any())).thenReturn(List.of());

        assertThat(resolver.resolve(tenant, employee, projectId.toString())).isEmpty();
    }

    @Test
    @DisplayName("No project reference, or one that is not an id, resolves to nobody and does not throw")
    void aBadReferenceGivesEmpty() {
        assertThat(resolver.resolve(tenant, employee, null)).isEmpty();
        assertThat(resolver.resolve(tenant, employee, "  ")).isEmpty();
        assertThat(resolver.resolve(tenant, employee, "not-a-uuid")).isEmpty();
    }
}
