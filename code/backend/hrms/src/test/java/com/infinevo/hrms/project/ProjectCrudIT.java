package com.infinevo.hrms.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test for Project, Task, and Assignment full lifecycle and CRUD operations (W-41).
 */
@SpringBootTest(classes = HrmsTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsProjectTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ProjectCrudIT extends AbstractIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private AssignmentService assignmentService;

    private UUID tenantId;
    private UUID employeeId;
    private UUID managerId;
    private EmployeeResponse currentEmployeeResponse;

    @BeforeEach
    void setUp() throws SQLException {
        tenantId = HrmsProjectTestSchema.insertTenant("CRUD Tenant " + UUID.randomUUID());
        employeeId = HrmsProjectTestSchema.insertEmployee(tenantId, "EMP-CRUD-" + UUID.randomUUID());
        managerId = HrmsProjectTestSchema.insertEmployee(tenantId, "MGR-CRUD-" + UUID.randomUUID());

        currentEmployeeResponse = new EmployeeResponse(
                employeeId,
                tenantId,
                "EMP-001",
                "John",
                null,
                "Doe",
                "MALE",
                LocalDate.now(),
                null,
                EmploymentStatus.ACTIVE,
                "john.doe@test.com",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());

        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Lifecycle: create project -> add task -> assign employee -> list mine -> soft delete cascade")
    void fullProjectLifecycle() throws SQLException {
        // 1. Create project with budget precision at scale 4
        BigDecimal originalBudget = new BigDecimal("123456.7890");
        ProjectRequest createRequest = new ProjectRequest(
                "HRMS Cloud Core",
                "Development",
                "Core system implementation",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                Priority.HIGH,
                ProjectStatus.STARTED,
                originalBudget,
                managerId);

        ProjectResponse project = projectService.create(createRequest);
        assertThat(project.id()).isNotNull();
        assertThat(project.name()).isEqualTo("HRMS Cloud Core");
        assertThat(project.budget()).isEqualByComparingTo(originalBudget);
        assertThat(project.budget().scale()).isEqualTo(4);

        // 2. Assign employee to project team
        AssignmentResponse assignment =
                assignmentService.assign(project.id(), new AssignmentRequest(employeeId, LocalDate.now()));
        assertThat(assignment.id()).isNotNull();
        assertThat(assignment.projectId()).isEqualTo(project.id());
        assertThat(assignment.employeeId()).isEqualTo(employeeId);

        List<AssignmentResponse> team = assignmentService.listByProject(project.id());
        assertThat(team).hasSize(1);
        assertThat(team.get(0).employeeId()).isEqualTo(employeeId);

        // 3. Create task assigned to employee
        TaskRequest taskRequest = new TaskRequest(
                "Build Authentication Screen",
                "Task for developer",
                employeeId,
                LocalDate.of(2026, 6, 30),
                Priority.HIGH,
                TaskStatus.TODO,
                16);

        TaskResponse task = taskService.create(project.id(), taskRequest);
        assertThat(task.id()).isNotNull();
        assertThat(task.projectId()).isEqualTo(project.id());
        assertThat(task.assigneeEmployeeId()).isEqualTo(employeeId);

        List<TaskResponse> tasks = taskService.listByProject(project.id(), null);
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).id()).isEqualTo(task.id());

        // 4. Test listMine for caller employee
        HrmsTestApp.CURRENT_EMPLOYEE.set(currentEmployeeResponse);
        List<ProjectResponse> myProjects = projectService.listMine();
        assertThat(myProjects).hasSize(1);
        assertThat(myProjects.get(0).id()).isEqualTo(project.id());

        List<TaskResponse> myTasks = taskService.listMine();
        assertThat(myTasks).hasSize(1);
        assertThat(myTasks.get(0).id()).isEqualTo(task.id());

        // 5. Update status and progress
        ProjectResponse statusUpdated = projectService.updateStatus(project.id(), ProjectStatus.COMPLETED);
        assertThat(statusUpdated.status()).isEqualTo(ProjectStatus.COMPLETED);

        ProjectResponse progressUpdated = projectService.updateProgress(project.id(), 85);
        assertThat(progressUpdated.progress()).isEqualTo(85);

        // 6. Soft delete cascades to tasks and assignments
        projectService.delete(project.id());

        assertThatThrownBy(() -> projectService.get(project.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> assignmentService.listByProject(project.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(projectService.listMine()).isEmpty();
        assertThat(taskService.listMine()).isEmpty();

        // 7. Verify soft deletion in database
        try (Connection conn = HrmsProjectTestSchema.migrationConnection()) {
            assertThat(isDeleted(conn, "hrms.project", project.id())).isTrue();
            assertThat(isDeleted(conn, "hrms.task", task.id())).isTrue();
            assertThat(isDeleted(conn, "hrms.assignment", assignment.id())).isTrue();
        }
    }

    @Test
    @DisplayName("Duplicate project name is case-insensitive: creating 'alpha' then 'ALPHA' in same tenant is refused")
    void duplicateProjectNameCaseInsensitive() {
        ProjectRequest first = new ProjectRequest(
                "alpha project",
                "Dev",
                "First",
                LocalDate.of(2026, 1, 1),
                null,
                Priority.MEDIUM,
                ProjectStatus.STARTED,
                BigDecimal.valueOf(1000),
                managerId);
        ProjectResponse created = projectService.create(first);
        assertThat(created.id()).isNotNull();

        ProjectRequest second = new ProjectRequest(
                "ALPHA PROJECT",
                "Dev",
                "Second",
                LocalDate.of(2026, 1, 1),
                null,
                Priority.HIGH,
                ProjectStatus.STARTED,
                BigDecimal.valueOf(2000),
                managerId);

        assertThatThrownBy(() -> projectService.create(second))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("DB unique index uk_project_tenant_name prevents inserting duplicate case-insensitive project names")
    void dbIndexEnforcesCaseInsensitiveUniqueness() throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.migrationConnection()) {
            HrmsProjectTestSchema.update(
                    "INSERT INTO hrms.project (tenant_id, name, priority, status) VALUES (?, 'CaseUnique', 'LOW', 'STARTED')",
                    tenantId);

            assertThatThrownBy(() -> HrmsProjectTestSchema.update(
                            "INSERT INTO hrms.project (tenant_id, name, priority, status) VALUES (?, 'caseunique', 'HIGH', 'STARTED')",
                            tenantId))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_project_tenant_name");
        }
    }

    @Test
    @DisplayName("DB unique index uk_assignment_tenant_project_employee prevents duplicate active employee assignment")
    void dbIndexEnforcesAssignmentUniqueness() throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.migrationConnection()) {
            UUID testProjectId = UUID.randomUUID();
            HrmsProjectTestSchema.update(
                    "INSERT INTO hrms.project (id, tenant_id, name, priority, status) VALUES (?, ?, 'AssignTestProject', 'LOW', 'STARTED')",
                    testProjectId,
                    tenantId);

            HrmsProjectTestSchema.update(
                    "INSERT INTO hrms.assignment (tenant_id, project_id, employee_id, assigned_on) VALUES (?, ?, ?, CURRENT_DATE)",
                    tenantId,
                    testProjectId,
                    employeeId);

            assertThatThrownBy(() -> HrmsProjectTestSchema.update(
                            "INSERT INTO hrms.assignment (tenant_id, project_id, employee_id, assigned_on) VALUES (?, ?, ?, CURRENT_DATE)",
                            tenantId,
                            testProjectId,
                            employeeId))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_assignment_tenant_project_employee");
        }
    }

    @Test
    @DisplayName("List: no filter, name or description search (any case), status filter, and literal % and _")
    void listSearchesNameAndDescription() {
        create("Payroll Portal", "Handles the monthly run", null);
        create("Website", "Marketing site refresh", null);
        create("Discount_100%", "Coupons", ProjectStatus.COMPLETED);

        // No search text: previously an untyped null reached PostgreSQL as bytea and lower(bytea) failed.
        assertThat(projectService.list(null, null, false)).hasSize(3);
        assertThat(projectService.list(null, "   ", false)).hasSize(3);

        assertThat(projectService.list(null, "PAYROLL", false))
                .extracting(ProjectResponse::name)
                .containsExactly("Payroll Portal");
        assertThat(projectService.list(null, "marketing", false))
                .extracting(ProjectResponse::name)
                .containsExactly("Website");

        assertThat(projectService.list(ProjectStatus.COMPLETED, null, false))
                .extracting(ProjectResponse::name)
                .containsExactly("Discount_100%");

        // % and _ are literal characters in what the user typed, not wildcards.
        assertThat(projectService.list(null, "%", false))
                .extracting(ProjectResponse::name)
                .containsExactly("Discount_100%");
        assertThat(projectService.list(null, "_", false))
                .extracting(ProjectResponse::name)
                .containsExactly("Discount_100%");
        assertThat(projectService.list(null, "no such text", false)).isEmpty();
    }

    @Test
    @DisplayName("List: managed=true returns only the caller's projects; task list filters by status or not at all")
    void managedAndTaskStatusFilters() {
        ProjectResponse mine = projectService.create(new ProjectRequest(
                "Managed By Me", "Internal", null, null, null, Priority.LOW, ProjectStatus.STARTED, null, employeeId));
        create("Managed By Someone Else", null, null);

        HrmsTestApp.CURRENT_EMPLOYEE.set(currentEmployeeResponse);
        assertThat(projectService.list(null, null, true))
                .extracting(ProjectResponse::name)
                .containsExactly("Managed By Me");
        assertThat(projectService.list(null, null, false)).hasSize(2);

        taskService.create(mine.id(), new TaskRequest("Open task", null, null, null, Priority.LOW, TaskStatus.TODO, 1));
        taskService.create(
                mine.id(), new TaskRequest("Done task", null, null, null, Priority.LOW, TaskStatus.COMPLETED, 1));
        assertThat(taskService.listByProject(mine.id(), null)).hasSize(2);
        assertThat(taskService.listByProject(mine.id(), TaskStatus.COMPLETED))
                .extracting(TaskResponse::title)
                .containsExactly("Done task");
    }

    private ProjectResponse create(String name, String description, ProjectStatus status) {
        return projectService.create(new ProjectRequest(
                name,
                "Internal",
                description,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                Priority.MEDIUM,
                status != null ? status : ProjectStatus.STARTED,
                BigDecimal.valueOf(100),
                managerId));
    }

    private static boolean isDeleted(Connection conn, String table, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT is_deleted FROM " + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
