package com.infinevo.hrms.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.hrms.project.HrmsTestApp;
import com.infinevo.shared.tenant.TenantContext;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-40.4 §7, {@code RegularizationRlsIT}: tenant A's requests are invisible to tenant B, on a raw {@code app_user}
 * connection and through the service.
 */
@SpringBootTest(classes = HrmsTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            HrmsAttendanceTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class RegularizationRlsIT extends AbstractIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Autowired
    private RegularizationService service;

    private UUID tenantA;
    private UUID tenantB;
    private UUID requestA;

    @BeforeEach
    void seed() throws SQLException {
        tenantA = HrmsAttendanceTestSchema.insertTenant("Regularization RLS A " + UUID.randomUUID());
        tenantB = HrmsAttendanceTestSchema.insertTenant("Regularization RLS B " + UUID.randomUUID());
        UUID employeeA = HrmsAttendanceTestSchema.insertEmployee(tenantA, "RLS-A-" + UUID.randomUUID());

        requestA = UUID.randomUUID();
        try (Connection conn = HrmsAttendanceTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("INSERT INTO hrms.attendance_regularization "
                        + "(id, tenant_id, employee_id, attendance_date, requested_in_at, requested_out_at, reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'Forgot')")) {
            ps.setObject(1, requestA);
            ps.setObject(2, tenantA);
            ps.setObject(3, employeeA);
            ps.setObject(4, java.sql.Date.valueOf(DAY));
            ps.setTimestamp(5, Timestamp.from(Instant.parse("2026-10-01T03:30:00Z")));
            ps.setTimestamp(6, Timestamp.from(Instant.parse("2026-10-01T12:30:00Z")));
            ps.executeUpdate();
        }
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static int visible(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT count(*) FROM hrms.attendance_regularization WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("As app_user, tenant A sees its request, tenant B and an unbound connection see nothing")
    void rawConnectionIsolation() throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.appConnection()) {
            assertThat(visible(conn, requestA)).isZero();
            HrmsAttendanceTestSchema.bindTenant(conn, tenantA);
            assertThat(visible(conn, requestA)).isEqualTo(1);
            HrmsAttendanceTestSchema.bindTenant(conn, tenantB);
            assertThat(visible(conn, requestA)).isZero();
        }
    }

    @Test
    @DisplayName("As app_user, tenant B cannot update tenant A's request")
    void crossTenantUpdateBlocked() throws SQLException {
        try (Connection conn = HrmsAttendanceTestSchema.appConnection()) {
            HrmsAttendanceTestSchema.bindTenant(conn, tenantB);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE hrms.attendance_regularization SET status = 'APPROVED' WHERE id = ?")) {
                ps.setObject(1, requestA);
                assertThat(ps.executeUpdate()).isZero();
            }
        }
    }

    @Test
    @DisplayName("Through the service, tenant B's read of the tenant's requests does not include tenant A's")
    void serviceReadIsolation() {
        TenantContext.set(tenantA);
        assertThat(service.all(DAY, DAY, null, null))
                .extracting(RegularizationResponse::id)
                .containsExactly(requestA);
        TenantContext.set(tenantB);
        assertThat(service.all(DAY, DAY, null, null)).isEmpty();
    }
}
