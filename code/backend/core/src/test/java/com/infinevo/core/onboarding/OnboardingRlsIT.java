package com.infinevo.core.onboarding;

import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_A;
import static com.infinevo.core.employee.EmployeeTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.CoreFeatureTestApp;
import com.infinevo.core.employee.EmployeeTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Row-Level Security integration test for onboarding task templates and employee tasks under {@code app_user} (W-102 §7).
 */
@SpringBootTest(classes = CoreFeatureTestApp.class)
class OnboardingRlsIT extends AbstractIntegrationTest {

    private UUID templateIdA;
    private UUID taskIdA;
    private UUID employeeIdA;

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        EmployeeTestSchema.apply();
        EmployeeTestSchema.seedTenants();
        employeeIdA = EmployeeTestSchema.seedEmployee(TENANT_A, "ONB-001", "Alex");

        templateIdA = UUID.randomUUID();
        taskIdA = UUID.randomUUID();

        try (Connection conn = EmployeeTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.onboarding_task_template "
                            + "(id, tenant_id, title, description, task_type, document_label, assigned_role, is_mandatory, due_days_after_joining, sort_order, is_active, created_by, updated_by) "
                            + "VALUES (?, ?, 'Template A', 'Description', 'MANUAL_TASK', NULL, 'EMPLOYEE', true, 7, 1, true, 'system', 'system')")) {
                ps.setObject(1, templateIdA);
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.employee_onboarding_task "
                            + "(id, tenant_id, employee_id, template_id, title, description, task_type, document_label, assigned_role, is_mandatory, due_date, status, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, 'Task A', 'Description', 'MANUAL_TASK', NULL, 'EMPLOYEE', true, ?, 'PENDING', 'system', 'system')")) {
                ps.setObject(1, taskIdA);
                ps.setObject(2, TENANT_A);
                ps.setObject(3, employeeIdA);
                ps.setObject(4, templateIdA);
                ps.setDate(5, Date.valueOf(LocalDate.now().plusDays(7)));
                ps.executeUpdate();
            }
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("RLS hides tenant A onboarding templates and tasks from tenant B under app_user")
    void tenantIsolation() throws SQLException {
        try (Connection conn = EmployeeTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try {
                // Bind to Tenant A -> can see own template and task
                bindTenant(conn, TENANT_A);
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.onboarding_task_template WHERE id = ?")) {
                    ps.setObject(1, templateIdA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                    }
                }
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.employee_onboarding_task WHERE id = ?")) {
                    ps.setObject(1, taskIdA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                    }
                }

                // Bind to Tenant B -> tenant A's rows are completely invisible
                bindTenant(conn, TENANT_B);
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.onboarding_task_template WHERE id = ?")) {
                    ps.setObject(1, templateIdA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isZero();
                    }
                }
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.employee_onboarding_task WHERE id = ?")) {
                    ps.setObject(1, taskIdA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isZero();
                    }
                }

                // Updating Tenant A rows as Tenant B does nothing
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.onboarding_task_template SET title = 'Hacked' WHERE id = ?")) {
                    ps.setObject(1, templateIdA);
                    int updated = ps.executeUpdate();
                    assertThat(updated).isZero();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.employee_onboarding_task SET title = 'Hacked' WHERE id = ?")) {
                    ps.setObject(1, taskIdA);
                    int updated = ps.executeUpdate();
                    assertThat(updated).isZero();
                }
            } finally {
                conn.rollback();
            }
        }
    }

    @Test
    @DisplayName("Unbound app_user connection sees zero onboarding templates and tasks")
    void unboundConnectionSeesNothing() throws SQLException {
        try (Connection conn = EmployeeTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.onboarding_task_template")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.employee_onboarding_task")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            } finally {
                conn.rollback();
            }
        }
    }

    private static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }
}

