package com.infinevo.hrms.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.hrms.attendance.HrmsAttendanceTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test for Row-Level Security (RLS) on employee goals and check-ins as {@code app_user} (W-106.2).
 */
@SpringBootTest(classes = HrmsTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsAttendanceTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class GoalRlsIT extends AbstractIntegrationTest {

    private UUID tenantA;
    private UUID tenantB;
    private UUID goalIdA;
    private UUID checkInIdA;
    private UUID empA;

    @BeforeEach
    void seed() throws SQLException {
        tenantA = HrmsAttendanceTestSchema.insertTenant("RLS Goal Tenant A " + UUID.randomUUID());
        tenantB = HrmsAttendanceTestSchema.insertTenant("RLS Goal Tenant B " + UUID.randomUUID());
        empA = HrmsAttendanceTestSchema.insertEmployee(
                tenantA, "GOAL-A-" + UUID.randomUUID().toString().substring(0, 6));

        goalIdA = UUID.randomUUID();
        checkInIdA = UUID.randomUUID();

        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO hrms.employee_goal "
                            + "(id, tenant_id, employee_id, cycle_id, title, description, category, metric_type, target_value, current_value, metric_unit, progress_percentage, start_date, due_date, status, created_by, updated_by) "
                            + "VALUES (?, ?, ?, NULL, 'Goal A', 'Description', 'INDIVIDUAL', 'PERCENTAGE', 100.00, 50.00, '%', 50, ?, ?, 'IN_PROGRESS', 'system', 'system')")) {
                ps.setObject(1, goalIdA);
                ps.setObject(2, tenantA);
                ps.setObject(3, empA);
                ps.setDate(4, Date.valueOf(LocalDate.of(2026, 1, 1)));
                ps.setDate(5, Date.valueOf(LocalDate.of(2026, 12, 31)));
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO hrms.employee_goal_check_in "
                    + "(id, tenant_id, goal_id, current_value, progress_percentage, notes, created_by) "
                    + "VALUES (?, ?, ?, ?, ?, 'Check-in note', 'system')")) {
                ps.setObject(1, checkInIdA);
                ps.setObject(2, tenantA);
                ps.setObject(3, goalIdA);
                ps.setBigDecimal(4, new BigDecimal("50.00"));
                ps.setInt(5, 50);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("Tenant A goals and check-ins are visible to Tenant A and invisible to Tenant B under app_user")
    void tenantIsolation() throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.appConnection()) {
            // Bind to tenant A -> can see own goal and check-in
            HrmsAttendanceTestSchema.bindTenant(conn, tenantA);
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM hrms.employee_goal WHERE id = ?")) {
                ps.setObject(1, goalIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM hrms.employee_goal_check_in WHERE id = ?")) {
                ps.setObject(1, checkInIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }

            // Bind to tenant B -> tenant A's rows are invisible
            HrmsAttendanceTestSchema.bindTenant(conn, tenantB);
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM hrms.employee_goal WHERE id = ?")) {
                ps.setObject(1, goalIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM hrms.employee_goal_check_in WHERE id = ?")) {
                ps.setObject(1, checkInIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
            }

            // Cannot update Tenant A's goal as Tenant B
            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE hrms.employee_goal SET title = 'Hacked' WHERE id = ?")) {
                ps.setObject(1, goalIdA);
                int updated = ps.executeUpdate();
                assertThat(updated).isZero();
            }
        }
    }
}
