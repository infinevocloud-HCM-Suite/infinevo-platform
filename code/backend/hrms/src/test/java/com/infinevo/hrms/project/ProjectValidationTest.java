package com.infinevo.hrms.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for project, task, and assignment business rules and validation (W-41).
 */
class ProjectValidationTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID otherEmployeeId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private ProjectRepository projectRepository;
    private TaskRepository taskRepository;
    private AssignmentRepository assignmentRepository;
    private EmployeeService employeeService;

    private ProjectService projectService;
    private TaskService taskService;
    private AssignmentService assignmentService;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);

        projectRepository = mock(ProjectRepository.class);
        taskRepository = mock(TaskRepository.class);
        assignmentRepository = mock(AssignmentRepository.class);
        employeeService = mock(EmployeeService.class);

        projectService =
                new ProjectServiceImpl(projectRepository, taskRepository, assignmentRepository, employeeService);
        taskService = new TaskServiceImpl(taskRepository, projectRepository, assignmentRepository, employeeService);
        assignmentService = new AssignmentServiceImpl(assignmentRepository, projectRepository, employeeService);

        EmployeeResponse mockManager = mock(EmployeeResponse.class);
        when(employeeService.get(managerId)).thenReturn(mockManager);

        EmployeeResponse mockEmployee = mock(EmployeeResponse.class);
        when(employeeService.get(employeeId)).thenReturn(mockEmployee);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Project createTestProject(String name, Priority priority, ProjectStatus status) {
        Project project = new Project(tenantId, name, priority, status, "test");
        project.setId(projectId);
        return project;
    }

    @Test
    @DisplayName("End date before start date is refused with 400 ValidationException")
    void endDateBeforeStartDateRefused() {
        ProjectRequest request = new ProjectRequest(
                "Invalid Dates Project",
                "Internal",
                "Description",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 5, 1),
                Priority.MEDIUM,
                ProjectStatus.STARTED,
                BigDecimal.valueOf(10000),
                managerId);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("end_date");
    }

    @Test
    @DisplayName("Negative budget is refused with 400 ValidationException")
    void negativeBudgetRefused() {
        ProjectRequest request = new ProjectRequest(
                "Negative Budget Project",
                "Internal",
                "Description",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                Priority.MEDIUM,
                ProjectStatus.STARTED,
                BigDecimal.valueOf(-500),
                managerId);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("budget");
    }

    @Test
    @DisplayName("Progress > 100 or < 0 is refused with 400 ValidationException")
    void progressOutOfRangeRefused() {
        Project project = createTestProject("Valid Project", Priority.MEDIUM, ProjectStatus.STARTED);
        when(projectRepository.findByIdAndTenantIdAndDeletedFalse(projectId, tenantId))
                .thenReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.updateProgress(projectId, 101))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("progress");

        assertThatThrownBy(() -> projectService.updateProgress(projectId, -1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("progress");
    }

    @Test
    @DisplayName("Task assignee not on the project team is refused with 400 ValidationException")
    void taskAssigneeNotOnTeamRefused() {
        Project project = createTestProject("Team Project", Priority.LOW, ProjectStatus.STARTED);
        when(projectRepository.findByIdAndTenantIdAndDeletedFalse(projectId, tenantId))
                .thenReturn(Optional.of(project));

        when(employeeService.get(otherEmployeeId)).thenReturn(mock(EmployeeResponse.class));
        when(assignmentRepository.existsByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse(
                        tenantId, projectId, otherEmployeeId))
                .thenReturn(false);

        TaskRequest taskRequest = new TaskRequest(
                "Task 1",
                "Task Description",
                otherEmployeeId,
                LocalDate.of(2026, 6, 1),
                Priority.HIGH,
                TaskStatus.TODO,
                8);

        assertThatThrownBy(() -> taskService.create(projectId, taskRequest))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("assignee_employee_id")
                .hasMessageContaining("team");
    }

    @Test
    @DisplayName("Assigning an already-assigned employee is refused with 409 DuplicateAssignmentException")
    void secondAssignmentRefusedWithConflict() {
        Project project = createTestProject("Project Alpha", Priority.LOW, ProjectStatus.STARTED);
        when(projectRepository.findByIdAndTenantIdAndDeletedFalse(projectId, tenantId))
                .thenReturn(Optional.of(project));

        when(assignmentRepository.existsByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse(
                        tenantId, projectId, employeeId))
                .thenReturn(true);

        AssignmentRequest request = new AssignmentRequest(employeeId, LocalDate.now());

        assertThatThrownBy(() -> assignmentService.assign(projectId, request))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining("already assigned");
    }

    @Test
    @DisplayName("Concurrent assignment race triggering DB unique index throws 409 DuplicateAssignmentException")
    void concurrentAssignmentIndexViolationThrowsConflict() {
        Project project = createTestProject("Project Alpha", Priority.LOW, ProjectStatus.STARTED);
        when(projectRepository.findByIdAndTenantIdAndDeletedFalse(projectId, tenantId))
                .thenReturn(Optional.of(project));

        when(assignmentRepository.existsByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse(
                        tenantId, projectId, employeeId))
                .thenReturn(false);

        when(assignmentRepository.saveAndFlush(any(Assignment.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "ERROR: duplicate key value violates unique constraint \"uk_assignment_tenant_project_employee\""));

        AssignmentRequest request = new AssignmentRequest(employeeId, LocalDate.now());

        assertThatThrownBy(() -> assignmentService.assign(projectId, request))
                .isInstanceOf(DuplicateAssignmentException.class)
                .hasMessageContaining("already assigned");
    }

    @Test
    @DisplayName("DataIntegrityViolationException for other constraints is not swallowed and rethrown")
    void otherDataIntegrityViolationIsRethrown() {
        Project project = createTestProject("Project Alpha", Priority.LOW, ProjectStatus.STARTED);
        when(projectRepository.findByIdAndTenantIdAndDeletedFalse(projectId, tenantId))
                .thenReturn(Optional.of(project));

        when(assignmentRepository.existsByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse(
                        tenantId, projectId, employeeId))
                .thenReturn(false);

        when(assignmentRepository.saveAndFlush(any(Assignment.class)))
                .thenThrow(new DataIntegrityViolationException("foreign key violation"));

        AssignmentRequest request = new AssignmentRequest(employeeId, LocalDate.now());

        assertThatThrownBy(() -> assignmentService.assign(projectId, request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("namesIndex identifies index name across nested cause chain")
    void namesIndexHelper() {
        Throwable nested = new RuntimeException(
                "detail", new IllegalStateException("caused by uk_assignment_tenant_project_employee violation"));
        assertThat(AssignmentServiceImpl.namesIndex(nested, "uk_assignment_tenant_project_employee"))
                .isTrue();
        assertThat(AssignmentServiceImpl.namesIndex(nested, "uk_other_index")).isFalse();
        assertThat(AssignmentServiceImpl.namesIndex(null, "uk_assignment_tenant_project_employee"))
                .isFalse();
    }

    @Test
    @DisplayName("Manager not found in tenant is refused with 400 ValidationException")
    void managerNotFoundRefused() {
        UUID unknownManagerId = UUID.randomUUID();
        when(employeeService.get(unknownManagerId)).thenThrow(new EmployeeService.NotFoundException(unknownManagerId));

        ProjectRequest request = new ProjectRequest(
                "No Manager Project",
                "Internal",
                "Description",
                LocalDate.of(2026, 1, 1),
                null,
                Priority.LOW,
                ProjectStatus.STARTED,
                null,
                unknownManagerId);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("manager_employee_id");
    }

    @Test
    @DisplayName("Duplicate project name within tenant is refused with 400 ValidationException")
    void duplicateProjectNameRefused() {
        when(projectRepository.existsByTenantIdAndNameIgnoreCaseAndDeletedFalse(tenantId, "Existing Project"))
                .thenReturn(true);

        ProjectRequest request = new ProjectRequest(
                "Existing Project",
                "Internal",
                "Description",
                LocalDate.of(2026, 1, 1),
                null,
                Priority.LOW,
                ProjectStatus.STARTED,
                null,
                managerId);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("Duplicate project name with different casing (case-insensitive) within tenant is refused with 400")
    void duplicateProjectNameCaseInsensitiveRefused() {
        when(projectRepository.existsByTenantIdAndNameIgnoreCaseAndDeletedFalse(tenantId, "ALPHA"))
                .thenReturn(true);

        ProjectRequest request = new ProjectRequest(
                "ALPHA",
                "Internal",
                "Description",
                LocalDate.of(2026, 1, 1),
                null,
                Priority.LOW,
                ProjectStatus.STARTED,
                null,
                managerId);

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("Updating project name to existing name with different casing is refused with 400 ValidationException")
    void updateProjectNameCaseInsensitiveRefused() {
        Project project = createTestProject("Beta", Priority.LOW, ProjectStatus.STARTED);
        when(projectRepository.findByIdAndTenantIdAndDeletedFalse(projectId, tenantId))
                .thenReturn(Optional.of(project));
        when(projectRepository.existsByTenantIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(tenantId, "ALPHA", projectId))
                .thenReturn(true);

        ProjectRequest request = new ProjectRequest(
                "ALPHA",
                "Internal",
                "Description",
                LocalDate.of(2026, 1, 1),
                null,
                Priority.LOW,
                ProjectStatus.STARTED,
                null,
                managerId);

        assertThatThrownBy(() -> projectService.update(projectId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("Deleting a project soft-deletes its tasks and assignments in the same transaction")
    void softDeleteCascadesToTasksAndAssignments() {
        Project project = createTestProject("To Delete", Priority.LOW, ProjectStatus.STARTED);
        when(projectRepository.findByIdAndTenantIdAndDeletedFalse(projectId, tenantId))
                .thenReturn(Optional.of(project));

        Task task = new Task(tenantId, projectId, "Subtask", Priority.LOW, TaskStatus.TODO, "test");
        Assignment assignment = new Assignment(tenantId, projectId, employeeId, LocalDate.now(), "test");

        when(taskRepository.findAllByTenantIdAndProjectIdAndDeletedFalse(tenantId, projectId))
                .thenReturn(List.of(task));
        when(assignmentRepository.findAllByTenantIdAndProjectIdAndDeletedFalse(tenantId, projectId))
                .thenReturn(List.of(assignment));

        projectService.delete(projectId);

        assertThat(project.isDeleted()).isTrue();
        assertThat(task.isDeleted()).isTrue();
        assertThat(assignment.isDeleted()).isTrue();
        verify(projectRepository).save(project);
        verify(taskRepository).saveAll(any());
        verify(assignmentRepository).saveAll(any());
    }

    @Test
    @DisplayName("ProjectActor defaults to 'system' when no SecurityContext exists")
    void projectActorDefaultsToSystem() {
        SecurityContextHolder.clearContext();
        assertThat(ProjectActor.currentActor()).isEqualTo(ProjectActor.ACTOR_SYSTEM);
    }

    @Test
    @DisplayName("ProjectActor extracts name and truncates to 100 characters")
    void projectActorTruncation() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("normal-actor", null, "ROLE_USER"));
        assertThat(ProjectActor.currentActor()).isEqualTo("normal-actor");

        String longName = "a".repeat(150);
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(longName, null, "ROLE_USER"));
        assertThat(ProjectActor.currentActor()).hasSize(100).isEqualTo("a".repeat(100));

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("   ", null, "ROLE_USER"));
        assertThat(ProjectActor.currentActor()).isEqualTo(ProjectActor.ACTOR_SYSTEM);
    }

    @Test
    @DisplayName("Writes capture actor attribution on create, update, and cascade delete")
    void writesCaptureActorAttribution() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("audit-user", null, "ROLE_USER"));

        // 1. Project create
        ProjectRequest projReq = new ProjectRequest(
                "Audit Project",
                "Internal",
                "Desc",
                LocalDate.of(2026, 1, 1),
                null,
                Priority.LOW,
                ProjectStatus.STARTED,
                null,
                managerId);
        Project savedProj = createTestProject("Audit Project", Priority.LOW, ProjectStatus.STARTED);
        when(projectRepository.save(any(Project.class))).thenReturn(savedProj);

        projectService.create(projReq);

        ArgumentCaptor<Project> projCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projCaptor.capture());
        assertThat(projCaptor.getValue().getCreatedBy()).isEqualTo("audit-user");
        assertThat(projCaptor.getValue().getUpdatedBy()).isEqualTo("audit-user");

        // 2. Project update
        when(projectRepository.findByIdAndTenantIdAndDeletedFalse(projectId, tenantId))
                .thenReturn(Optional.of(savedProj));
        projectService.update(projectId, projReq);
        assertThat(savedProj.getUpdatedBy()).isEqualTo("audit-user");

        // 3. Project update status & progress
        projectService.updateStatus(projectId, ProjectStatus.COMPLETED);
        assertThat(savedProj.getUpdatedBy()).isEqualTo("audit-user");
        projectService.updateProgress(projectId, 50);
        assertThat(savedProj.getUpdatedBy()).isEqualTo("audit-user");

        // 4. Task create & update & delete
        Task savedTask = new Task(tenantId, projectId, "Audit Task", Priority.LOW, TaskStatus.TODO, "test");
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);
        when(taskRepository.findByIdAndTenantIdAndDeletedFalse(any(), any())).thenReturn(Optional.of(savedTask));

        TaskRequest taskReq = new TaskRequest("Audit Task", null, null, null, Priority.LOW, TaskStatus.TODO, null);
        taskService.create(projectId, taskReq);

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getCreatedBy()).isEqualTo("audit-user");
        assertThat(taskCaptor.getValue().getUpdatedBy()).isEqualTo("audit-user");

        taskService.update(savedTask.getId(), taskReq);
        assertThat(savedTask.getUpdatedBy()).isEqualTo("audit-user");
        taskService.updateStatus(savedTask.getId(), TaskStatus.COMPLETED);
        assertThat(savedTask.getUpdatedBy()).isEqualTo("audit-user");
        taskService.delete(savedTask.getId());
        assertThat(savedTask.getUpdatedBy()).isEqualTo("audit-user");

        // 5. Assignment create & remove
        Assignment savedAssign = new Assignment(tenantId, projectId, employeeId, LocalDate.now(), "test");
        when(assignmentRepository.saveAndFlush(any(Assignment.class))).thenReturn(savedAssign);
        when(assignmentRepository.findByTenantIdAndProjectIdAndEmployeeIdAndDeletedFalse(
                        tenantId, projectId, employeeId))
                .thenReturn(Optional.of(savedAssign));

        assignmentService.assign(projectId, new AssignmentRequest(employeeId, LocalDate.now()));
        ArgumentCaptor<Assignment> assignCaptor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).saveAndFlush(assignCaptor.capture());
        assertThat(assignCaptor.getValue().getCreatedBy()).isEqualTo("audit-user");
        assertThat(assignCaptor.getValue().getUpdatedBy()).isEqualTo("audit-user");

        assignmentService.remove(projectId, employeeId);
        assertThat(savedAssign.getUpdatedBy()).isEqualTo("audit-user");
    }
}
