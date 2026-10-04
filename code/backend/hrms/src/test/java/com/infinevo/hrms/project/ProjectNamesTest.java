package com.infinevo.hrms.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * W-48.1 section 4: project, assignment and task reads fill display names from one batch read of every id on the
 * page, and an id with no employee gives a {@code null} name rather than an error.
 */
class ProjectNamesTest {

    private final UUID tenant = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();
    private final UUID known = UUID.randomUUID();
    private final UUID missing = UUID.randomUUID();

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final TimesheetUsage timesheetUsage = mock(TimesheetUsage.class);

    @BeforeEach
    void bind() {
        TenantContext.set(tenant);
        when(employeeService.displayNames(anyCollection())).thenReturn(Map.of(manager, "Mira Sen", known, "Ravi Das"));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private Project project(String name) {
        Project p = new Project();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenant);
        p.setName(name);
        p.setManagerEmployeeId(manager);
        return p;
    }

    private Assignment assignment(UUID projectId, UUID employeeId) {
        return new Assignment(tenant, projectId, employeeId, java.time.LocalDate.now(), "test");
    }

    @Test
    @DisplayName("Project list: one displayNames call for the page; manager and team named, missing id null")
    @SuppressWarnings("unchecked")
    void projectListBatchesNames() {
        Project a = project("A");
        Project b = project("B");
        when(projectRepository.searchProjects(eq(tenant), any(), eq(true), any(), any()))
                .thenReturn(List.of(a, b));
        when(assignmentRepository.findAllByTenantIdAndProjectIdAndDeletedFalse(tenant, a.getId()))
                .thenReturn(List.of(assignment(a.getId(), known)));
        when(assignmentRepository.findAllByTenantIdAndProjectIdAndDeletedFalse(tenant, b.getId()))
                .thenReturn(List.of(assignment(b.getId(), missing)));

        ProjectServiceImpl service = new ProjectServiceImpl(
                projectRepository, taskRepository, assignmentRepository, employeeService, timesheetUsage);
        List<ProjectResponse> out = service.list(null, null, false);

        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(employeeService, times(1)).displayNames(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(manager, known, missing);

        assertThat(out.get(0).managerName()).isEqualTo("Mira Sen");
        assertThat(out.get(0).team()).containsExactly(new ProjectResponse.TeamMember(known, "Ravi Das"));
        assertThat(out.get(1).team()).containsExactly(new ProjectResponse.TeamMember(missing, null));
        assertThat(out.get(1).teamMemberIds()).containsExactly(missing);
    }

    @Test
    @DisplayName("Assignment list: one displayNames call; missing employee gives a null name")
    void assignmentListBatchesNames() {
        Project p = project("P");
        when(projectRepository.findByIdAndTenantIdAndDeletedFalse(p.getId(), tenant))
                .thenReturn(java.util.Optional.of(p));
        when(assignmentRepository.findAllByTenantIdAndProjectIdAndDeletedFalse(tenant, p.getId()))
                .thenReturn(List.of(assignment(p.getId(), known), assignment(p.getId(), missing)));

        AssignmentServiceImpl service =
                new AssignmentServiceImpl(assignmentRepository, projectRepository, employeeService);
        List<AssignmentResponse> out = service.listByProject(p.getId());

        verify(employeeService, times(1)).displayNames(anyCollection());
        assertThat(out).extracting(AssignmentResponse::employeeName).containsExactly("Ravi Das", null);
    }

    @Test
    @DisplayName("Task list: one displayNames call and one project read; missing assignee gives a null name")
    void taskListBatchesNames() {
        Project p = project("Portal");
        when(projectRepository.findByIdAndTenantIdAndDeletedFalse(p.getId(), tenant))
                .thenReturn(java.util.Optional.of(p));
        when(projectRepository.findAllByTenantIdAndIdInAndDeletedFalse(tenant, Set.of(p.getId())))
                .thenReturn(List.of(p));
        Task t1 = task(p.getId(), known);
        Task t2 = task(p.getId(), missing);
        Task t3 = task(p.getId(), null);
        when(taskRepository.findByTenantIdAndProjectIdAndStatusIn(tenant, p.getId(), EnumSet.allOf(TaskStatus.class)))
                .thenReturn(List.of(t1, t2, t3));

        TaskServiceImpl service = new TaskServiceImpl(
                taskRepository, projectRepository, assignmentRepository, employeeService, timesheetUsage);
        List<TaskResponse> out = service.listByProject(p.getId(), null);

        verify(employeeService, times(1)).displayNames(anyCollection());
        verify(projectRepository, times(1)).findAllByTenantIdAndIdInAndDeletedFalse(eq(tenant), anyCollection());
        assertThat(out).extracting(TaskResponse::assigneeName).containsExactly("Ravi Das", null, null);
        assertThat(out).extracting(TaskResponse::projectName).containsOnly("Portal");
    }

    private Task task(UUID projectId, UUID assignee) {
        Task t = new Task();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenant);
        t.setProjectId(projectId);
        t.setTitle("t");
        t.setAssigneeEmployeeId(assignee);
        return t;
    }
}
