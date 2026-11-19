package com.infinevo.hrms.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.hrms.attendance.HrmsAttendanceTestSchema;
import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
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
 * Integration test for Row-Level Security (RLS) on performance cycles and reviews as {@code app_user} (W-106.1).
 */
@SpringBootTest(classes = HrmsTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsAttendanceTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PerformanceRlsIT extends AbstractIntegrationTest {

    private UUID tenantA;
    private UUID tenantB;
    private UUID cycleIdA;
    private UUID reviewIdA;
    private UUID empA;

    @BeforeEach
    void seed() throws SQLException {
        tenantA = HrmsAttendanceTestSchema.insertTenant("RLS Perf Tenant A " + UUID.randomUUID());
        tenantB = HrmsAttendanceTestSchema.insertTenant("RLS Perf Tenant B " + UUID.randomUUID());
        empA = HrmsAttendanceTestSchema.insertEmployee(
                tenantA, "PERF-A-" + UUID.randomUUID().toString().substring(0, 6));

        cycleIdA = UUID.randomUUID();
        reviewIdA = UUID.randomUUID();

        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO hrms.performance_cycle "
                    + "(id, tenant_id, name, period_start, period_end, self_review_deadline, manager_review_deadline, rating_scale_max, status) "
                    + "VALUES (?, ?, 'Cycle A', ?, ?, ?, ?, 5, 'ACTIVE')")) {
                ps.setObject(1, cycleIdA);
                ps.setObject(2, tenantA);
                ps.setDate(3, Date.valueOf(LocalDate.of(2026, 1, 1)));
                ps.setDate(4, Date.valueOf(LocalDate.of(2026, 12, 31)));
                ps.setDate(5, Date.valueOf(LocalDate.of(2026, 11, 15)));
                ps.setDate(6, Date.valueOf(LocalDate.of(2026, 12, 15)));
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO hrms.performance_review "
                    + "(id, tenant_id, cycle_id, employee_id, manager_id, status) "
                    + "VALUES (?, ?, ?, ?, NULL, 'PENDING_SELF_REVIEW')")) {
                ps.setObject(1, reviewIdA);
                ps.setObject(2, tenantA);
                ps.setObject(3, cycleIdA);
                ps.setObject(4, empA);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("Tenant A rows are visible to Tenant A and completely invisible to Tenant B under app_user")
    void tenantIsolation() throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.appConnection()) {
            // Bind to tenant A -> can see own cycle and review
            HrmsAttendanceTestSchema.bindTenant(conn, tenantA);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM hrms.performance_cycle WHERE id = ?")) {
                ps.setObject(1, cycleIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM hrms.performance_review WHERE id = ?")) {
                ps.setObject(1, reviewIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }

            // Bind to tenant B -> tenant A's rows are invisible
            HrmsAttendanceTestSchema.bindTenant(conn, tenantB);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM hrms.performance_cycle WHERE id = ?")) {
                ps.setObject(1, cycleIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM hrms.performance_review WHERE id = ?")) {
                ps.setObject(1, reviewIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
            }

            // Tenant B cannot update Tenant A's review
            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE hrms.performance_review SET self_comments = 'Hacked' WHERE id = ?")) {
                ps.setObject(1, reviewIdA);
                int updated = ps.executeUpdate();
                assertThat(updated).isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("Unbound connection sees zero cycles and reviews under app_user")
    void unboundConnectionSeesZeroRows() throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.appConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM hrms.performance_cycle")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM hrms.performance_review")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
            }
        }
    }
}
