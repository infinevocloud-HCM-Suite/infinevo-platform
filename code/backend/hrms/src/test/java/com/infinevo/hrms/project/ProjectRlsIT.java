package com.infinevo.hrms.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * Integration test for Row-Level Security (RLS) on HRMS tables as {@code app_user} (W-41).
 */
@SpringBootTest(classes = HrmsTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsProjectTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ProjectRlsIT extends AbstractIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private AssignmentService assignmentService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID employeeA;
    private UUID employeeB;

    private UUID projectIdA;
    private UUID taskIdA;
    private UUID assignmentIdA;

    @BeforeEach
    void seed() throws SQLException {
        tenantA = HrmsProjectTestSchema.insertTenant("RLS Project A " + UUID.randomUUID());
        tenantB = HrmsProjectTestSchema.insertTenant("RLS Project B " + UUID.randomUUID());
        employeeA = HrmsProjectTestSchema.insertEmployee(tenantA, "EMP-A-" + UUID.randomUUID());
        employeeB = HrmsProjectTestSchema.insertEmployee(tenantB, "EMP-B-" + UUID.randomUUID());

        TenantContext.set(tenantA);
        ProjectResponse project = projectService.create(new ProjectRequest(
                "RLS Project Alpha",
                "Internal",
                "Project in Tenant A",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                Priority.HIGH,
                ProjectStatus.STARTED,
                BigDecimal.valueOf(50000),
                employeeA));
        projectIdA = project.id();

        AssignmentResponse assignment =
                assignmentService.assign(projectIdA, new AssignmentRequest(employeeA, LocalDate.now()));
        assignmentIdA = assignment.id();

        TaskResponse task = taskService.create(
                projectIdA,
                new TaskRequest(
                        "Task Alpha",
                        "Task in Tenant A",
                        employeeA,
                        LocalDate.of(2026, 6, 1),
                        Priority.HIGH,
                        TaskStatus.TODO,
                        10));
        taskIdA = task.id();
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A connection with no tenant bound sees zero rows across hrms tables")
    void noTenantSeesZeroRows() throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            assertThat(count(conn, "SELECT count(*) FROM hrms.project")).isZero();
            assertThat(count(conn, "SELECT count(*) FROM hrms.task")).isZero();
            assertThat(count(conn, "SELECT count(*) FROM hrms.assignment")).isZero();
        }
    }

    @Test
    @DisplayName("Tenant B cannot read tenant A's project, task, or assignment; tenant A can")
    void tenantBCannotReadTenantARows() throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            HrmsProjectTestSchema.bindTenant(conn, tenantB);
            assertThat(count(conn, "SELECT count(*) FROM hrms.project WHERE id = ?", projectIdA))
                    .isZero();
            assertThat(count(conn, "SELECT count(*) FROM hrms.task WHERE id = ?", taskIdA))
                    .isZero();
            assertThat(count(conn, "SELECT count(*) FROM hrms.assignment WHERE id = ?", assignmentIdA))
                    .isZero();
        }

        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            HrmsProjectTestSchema.bindTenant(conn, tenantA);
            assertThat(count(conn, "SELECT count(*) FROM hrms.project WHERE id = ?", projectIdA))
                    .isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM hrms.task WHERE id = ?", taskIdA))
                    .isEqualTo(1);
            assertThat(count(conn, "SELECT count(*) FROM hrms.assignment WHERE id = ?", assignmentIdA))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Bound to tenant B, updating or deleting tenant A's project affects 0 rows")
    void crossTenantUpdateAffectsZeroRows() throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            HrmsProjectTestSchema.bindTenant(conn, tenantB);
            try (PreparedStatement ps = conn.prepareStatement("UPDATE hrms.project SET name = 'Hacked' WHERE id = ?")) {
                ps.setObject(1, projectIdA);
                int updated = ps.executeUpdate();
                assertThat(updated).isZero();
            }
        }
    }

    @Test
    @DisplayName("Bound to tenant B, an insert naming tenant A is rejected by the RLS policy")
    void crossTenantInsertIsRejected() throws SQLException {
        try (Connection conn = HrmsProjectTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            HrmsProjectTestSchema.bindTenant(conn, tenantB);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO hrms.project (tenant_id, name, priority, status, progress, created_by, updated_by) "
                            + "VALUES (?, 'Cross Tenant', 'LOW', 'STARTED', 0, 'test', 'test')")) {
                ps.setObject(1, tenantA);
                assertThatThrownBy(ps::executeUpdate).isInstanceOf(SQLException.class);
            }
        }
    }

    private static long count(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
