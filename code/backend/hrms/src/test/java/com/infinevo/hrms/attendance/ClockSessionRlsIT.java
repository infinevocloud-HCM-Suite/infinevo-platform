package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * Integration test for Row-Level Security (RLS) on {@code hrms.clock_session} as {@code app_user} (W-40.3, spec section 7).
 *
 * <p>Proves:
 * <ul>
 *   <li>Tenant A's clock sessions are visible to Tenant A and invisible to Tenant B under {@code app_user}</li>
 *   <li>A connection with no tenant bound sees 0 rows</li>
 *   <li>Tenant B cannot update Tenant A's session</li>
 * </ul>
 */
@SpringBootTest(classes = HrmsTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsAttendanceTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class ClockSessionRlsIT extends AbstractIntegrationTest {

    private UUID tenantA;
    private UUID tenantB;
    private UUID employeeA;
    private UUID sessionIdA;

    @BeforeEach
    void seed() throws SQLException {
        tenantA = HrmsAttendanceTestSchema.insertTenant("RLS Tenant A " + UUID.randomUUID());
        tenantB = HrmsAttendanceTestSchema.insertTenant("RLS Tenant B " + UUID.randomUUID());

        employeeA = HrmsAttendanceTestSchema.insertEmployee(
                tenantA, "EMP-A-" + UUID.randomUUID().toString().substring(0, 8));

        sessionIdA = UUID.randomUUID();
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO hrms.clock_session "
                        + "(id, tenant_id, employee_id, attendance_date, clock_in_at, origin, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, ?, 'CLOCK', 'test', 'test')")) {
            ps.setObject(1, sessionIdA);
            ps.setObject(2, tenantA);
            ps.setObject(3, employeeA);
            ps.setObject(4, java.sql.Date.valueOf(LocalDate.of(2026, 10, 1)));
            ps.setTimestamp(5, Timestamp.from(Instant.parse("2026-10-01T09:00:00Z")));
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Tenant A's clock session is visible to Tenant A and invisible to Tenant B under app_user")
    void tenantIsolation() throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.appConnection()) {
            // Bind to tenant A -> can see own session row
            HrmsAttendanceTestSchema.bindTenant(conn, tenantA);
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM hrms.clock_session WHERE id = ?")) {
                ps.setObject(1, sessionIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }

            // Bind to tenant B -> tenant A's row is invisible
            HrmsAttendanceTestSchema.bindTenant(conn, tenantB);
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM hrms.clock_session WHERE id = ?")) {
                ps.setObject(1, sessionIdA);
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
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM hrms.clock_session")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
            }
        }
    }

    @Test
    @DisplayName("Tenant B cannot update Tenant A's clock session under app_user")
    void crossTenantUpdateBlocked() throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.appConnection()) {
            HrmsAttendanceTestSchema.bindTenant(conn, tenantB);
            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE hrms.clock_session SET clock_out_at = ? WHERE id = ?")) {
                ps.setTimestamp(1, Timestamp.from(Instant.parse("2026-10-01T17:00:00Z")));
                ps.setObject(2, sessionIdA);
                int updated = ps.executeUpdate();
                assertThat(updated).isEqualTo(0);
            }
        }

        // Verify session clock_out_at remains null in tenant A
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT clock_out_at FROM hrms.clock_session WHERE id = ?")) {
            ps.setObject(1, sessionIdA);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp(1)).isNull();
            }
        }
    }
}
