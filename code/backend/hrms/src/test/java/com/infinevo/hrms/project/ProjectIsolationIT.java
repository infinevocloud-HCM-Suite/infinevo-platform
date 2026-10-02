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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-41 review findings that were claims without a test: an employee of another tenant cannot be put on a team or
 * given a task, a task stays with its assignee, and a budget is stored as the database holds it, not as the
 * entity in memory still shows it.
 */
@SpringBootTest(classes = HrmsTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsProjectTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ProjectIsolationIT extends AbstractIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private AssignmentService assignmentService;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID employeeId;
    private UUID teammateId;
    private UUID strangerId;
    private UUID managerId;

    @BeforeEach
    void setUp() throws SQLException {
        tenantId = HrmsProjectTestSchema.insertTenant("Isolation A " + UUID.randomUUID());
        otherTenantId = HrmsProjectTestSchema.insertTenant("Isolation B " + UUID.randomUUID());
        employeeId = HrmsProjectTestSchema.insertEmployee(tenantId, "EMP-A-" + UUID.randomUUID());
        teammateId = HrmsProjectTestSchema.insertEmployee(tenantId, "EMP-T-" + UUID.randomUUID());
        managerId = HrmsProjectTestSchema.insertEmployee(tenantId, "MGR-A-" + UUID.randomUUID());
        strangerId = HrmsProjectTestSchema.insertEmployee(otherTenantId, "EMP-B-" + UUID.randomUUID());
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        TenantContext.clear();
    }

    private ProjectResponse project(String name, BigDecimal budget) {
        return projectService.create(new ProjectRequest(
                name,
                "Dev",
                "Isolation test",
                LocalDate.of(2026, 1, 1),
                null,
                Priority.MEDIUM,
                ProjectStatus.STARTED,
                budget,
                managerId));
    }

    private static EmployeeResponse actingAs(UUID employee, UUID tenant) {
        return new EmployeeResponse(
                employee,
                tenant,
                "E-" + employee.toString().substring(0, 4),
                "Acting",
                null,
                "Employee",
                "MALE",
                LocalDate.now(),
                null,
                EmploymentStatus.ACTIVE,
                employee + "@test.local",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }

    @Test
    @DisplayName("An employee of another tenant cannot be assigned to a project, and no assignment row is written")
    void crossTenantEmployeeCannotBeAssigned() throws SQLException {
        ProjectResponse project = project("Isolation Team " + UUID.randomUUID(), BigDecimal.valueOf(100));

        assertThatThrownBy(() -> assignmentService.assign(project.id(), new AssignmentRequest(strangerId, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("employee");

        assertThat(HrmsProjectTestSchema.count(
                        "SELECT count(*) FROM hrms.assignment WHERE project_id = ? AND employee_id = ?",
                        project.id(),
                        strangerId))
                .as("nothing was stored for the stranger")
                .isZero();
        assertThat(assignmentService.listByProject(project.id())).isEmpty();
    }

    @Test
    @DisplayName("An employee of another tenant cannot be given a task, even with the project's team empty or full")
    void crossTenantEmployeeCannotBeGivenATask() throws SQLException {
        ProjectResponse project = project("Isolation Task " + UUID.randomUUID(), BigDecimal.valueOf(100));
        assignmentService.assign(project.id(), new AssignmentRequest(employeeId, null));

        assertThatThrownBy(() -> taskService.create(project.id(), task("For a stranger", strangerId)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("assignee");

        assertThat(HrmsProjectTestSchema.count("SELECT count(*) FROM hrms.task WHERE project_id = ?", project.id()))
                .isZero();
    }

    @Test
    @DisplayName(
            "/tasks/mine returns only the caller's own tasks; a teammate's, an unassigned one and a deleted one stay out")
    void myTasksAreOnlyMine() {
        ProjectResponse project = project("Isolation Mine " + UUID.randomUUID(), BigDecimal.valueOf(100));
        assignmentService.assign(project.id(), new AssignmentRequest(employeeId, null));
        assignmentService.assign(project.id(), new AssignmentRequest(teammateId, null));
        TaskResponse mine = taskService.create(project.id(), task("Mine", employeeId));
        TaskResponse theirs = taskService.create(project.id(), task("Teammate's", teammateId));
        taskService.create(project.id(), task("Nobody's", null));
        TaskResponse gone = taskService.create(project.id(), task("Mine, since deleted", employeeId));
        taskService.delete(gone.id());

        HrmsTestApp.CURRENT_EMPLOYEE.set(actingAs(employeeId, tenantId));
        assertThat(taskService.listMine()).extracting(TaskResponse::id).containsExactly(mine.id());

        HrmsTestApp.CURRENT_EMPLOYEE.set(actingAs(teammateId, tenantId));
        assertThat(taskService.listMine()).extracting(TaskResponse::id).containsExactly(theirs.id());

        HrmsTestApp.CURRENT_EMPLOYEE.remove();
        assertThat(taskService.listMine()).as("no current employee, no tasks").isEmpty();
    }

    @Test
    @DisplayName("The budget is held at scale 4 in the database row, on create and on update")
    void budgetIsStoredAtScaleFour() throws SQLException {
        ProjectResponse created = project("Isolation Budget " + UUID.randomUUID(), new BigDecimal("10.5"));
        assertThat(storedBudget(created.id())).isEqualTo("10.5000");

        ProjectResponse precise = project("Isolation Precise " + UUID.randomUUID(), new BigDecimal("123456.7890"));
        assertThat(storedBudget(precise.id())).isEqualTo("123456.7890");

        projectService.update(
                created.id(),
                new ProjectRequest(
                        created.name(),
                        "Dev",
                        "Updated",
                        LocalDate.of(2026, 1, 1),
                        null,
                        Priority.MEDIUM,
                        ProjectStatus.STARTED,
                        new BigDecimal("2500"),
                        managerId));
        assertThat(storedBudget(created.id())).isEqualTo("2500.0000");

        assertThatThrownBy(() -> project("Isolation Negative " + UUID.randomUUID(), new BigDecimal("-1")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("budget");
    }

    private TaskRequest task(String title, UUID assignee) {
        return new TaskRequest(
                title, "Isolation test", assignee, LocalDate.of(2026, 6, 30), Priority.MEDIUM, TaskStatus.TODO, 4);
    }

    /** The budget as the row holds it, read as the schema owner and not through the entity. */
    private String storedBudget(UUID projectId) throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT budget::text FROM hrms.project WHERE id = ?")) {
            ps.setObject(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }
}
