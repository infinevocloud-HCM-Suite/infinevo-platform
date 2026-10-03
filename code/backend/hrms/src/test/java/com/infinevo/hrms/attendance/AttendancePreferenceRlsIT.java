package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test for Row-Level Security (RLS) on {@code hrms.attendance_preference} as {@code app_user} (W-40.1, spec section 7).
 *
 * <p>Proves:
 * <ul>
 *   <li>Tenant A's preference row is completely invisible to Tenant B under {@code app_user}</li>
 *   <li>A connection with no tenant bound sees 0 rows</li>
 *   <li>Tenant B cannot update Tenant A's row</li>
 * </ul>
 */
@SpringBootTest(classes = HrmsTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsAttendanceTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class AttendancePreferenceRlsIT extends AbstractIntegrationTest {

    private UUID tenantA;
    private UUID tenantB;
    private UUID prefIdA;

    @BeforeEach
    void seed() throws SQLException {
        tenantA = HrmsAttendanceTestSchema.insertTenant("RLS Tenant A " + UUID.randomUUID());
        tenantB = HrmsAttendanceTestSchema.insertTenant("RLS Tenant B " + UUID.randomUUID());

        prefIdA = UUID.randomUUID();
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO hrms.attendance_preference "
                        + "(id, tenant_id, hours_calculation, full_day_minimum_hours, half_day_minimum_hours, regularization_window_days, max_regularizations_per_month, allow_regularization_without_session) "
                        + "VALUES (?, ?, 'EVERY_SESSION', 9.00, 4.50, 14, 5, true)")) {
            ps.setObject(1, prefIdA);
            ps.setObject(2, tenantA);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Tenant A's preference row is visible to Tenant A and invisible to Tenant B under app_user")
    void tenantIsolation() throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.appConnection()) {
            // Bind to tenant A -> can see own preference row
            HrmsAttendanceTestSchema.bindTenant(conn, tenantA);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM hrms.attendance_preference WHERE id = ?")) {
                ps.setObject(1, prefIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }

            // Bind to tenant B -> tenant A's row is invisible
            HrmsAttendanceTestSchema.bindTenant(conn, tenantB);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM hrms.attendance_preference WHERE id = ?")) {
                ps.setObject(1, prefIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
            }
        }
    }

    @Test
    @DisplayName("Connection with no tenant bound sees 0 rows under app_user")
    void unboundConnectionSeesZeroRows() throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.appConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM hrms.attendance_preference")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
            }
        }
    }

    @Test
    @DisplayName("Tenant B cannot update Tenant A's preference row under app_user")
    void crossTenantUpdateBlocked() throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.appConnection()) {
            HrmsAttendanceTestSchema.bindTenant(conn, tenantB);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE hrms.attendance_preference SET full_day_minimum_hours = ? WHERE id = ?")) {
                ps.setBigDecimal(1, BigDecimal.valueOf(10.00));
                ps.setObject(2, prefIdA);
                int updated = ps.executeUpdate();
                assertThat(updated).isEqualTo(0);
            }
        }

        // Verify row was untouched in tenant A
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT full_day_minimum_hours FROM hrms.attendance_preference WHERE id = ?")) {
            ps.setObject(1, prefIdA);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBigDecimal(1)).isEqualByComparingTo(BigDecimal.valueOf(9.00));
            }
        }
    }
}
