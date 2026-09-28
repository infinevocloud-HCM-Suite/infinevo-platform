package com.infinevo.core.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.audit.CoreAuditTestApp;
import com.infinevo.core.employee.EmployeeTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-39.1, spec section 7 — {@code AttendanceUpsertIT}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>A second {@code PUT} of the same day updates the existing record, leaving table row count unchanged.
 *   <li>The write is audited: an audit row is written to {@code core.audit_log} through {@code @Audited}.
 * </ul>
 */
@SpringBootTest(classes = CoreAuditTestApp.class)
class AttendanceUpsertIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = AttendanceTestSchema.TENANT_A;

    @Autowired
    private AttendanceService attendanceService;

    private UUID employeeId;

    @BeforeAll
    static void applySchema() throws Exception {
        AttendanceTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws Exception {
        TenantContext.clear();
        AttendanceTestSchema.clearAttendance();
        AttendanceTestSchema.clearAudit();
        EmployeeTestSchema.clearEmployees();
    }

    @BeforeEach
    void seed() throws Exception {
        EmployeeTestSchema.seedTenants();
        AttendanceTestSchema.clearAttendance();
        AttendanceTestSchema.clearAudit();
        EmployeeTestSchema.clearEmployees();

        employeeId = EmployeeTestSchema.seedEmployee(TENANT_A, "ATT-UPSERT-1", "Alice");
    }

    @AfterEach
    void unbind() throws Exception {
        TenantContext.clear();
        AttendanceTestSchema.clearAttendance();
        AttendanceTestSchema.clearAudit();
        EmployeeTestSchema.clearEmployees();
    }

    @Test
    @DisplayName("Second PUT of the same day updates existing record, row count unchanged, and audit log captures")
    void secondPutUpdatesAndCapturesAudit() throws Exception {
        TenantContext.set(TENANT_A);
        LocalDate date = LocalDate.now().minusDays(2);

        // First PUT
        List<AttendanceResponse> firstResult = attendanceService.upsert(
                List.of(new AttendanceEntry(employeeId, date, AttendanceStatus.PRESENT, "Full day")));
        assertThat(firstResult).hasSize(1);
        UUID firstId = firstResult.get(0).id();
        assertThat(firstResult.get(0).status()).isEqualTo(AttendanceStatus.PRESENT);

        assertThat(AttendanceTestSchema.rawRowCount("attendance")).isEqualTo(1);

        // Second PUT of the same day
        List<AttendanceResponse> secondResult = attendanceService.upsert(
                List.of(new AttendanceEntry(employeeId, date, AttendanceStatus.HALF_DAY, "Left at noon")));
        assertThat(secondResult).hasSize(1);
        assertThat(secondResult.get(0).id()).isEqualTo(firstId);
        assertThat(secondResult.get(0).status()).isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(secondResult.get(0).remarks()).isEqualTo("Left at noon");

        // Row count remains exactly 1 (idempotent update, not duplicate)
        assertThat(AttendanceTestSchema.rawRowCount("attendance")).isEqualTo(1);

        // Verify the persisted state in DB
        List<AttendanceResponse> listed = attendanceService.list(date, date, employeeId);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).status()).isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(listed.get(0).remarks()).isEqualTo("Left at noon");

        // Verify audit log has captured the change
        assertThat(countAuditRows(TENANT_A, "attendance"))
                .as("audit row must be recorded for attendance entity")
                .isGreaterThanOrEqualTo(1);
    }

    private static int countAuditRows(UUID tenantId, String table) throws SQLException {
        try (Connection conn = AttendanceTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM core.audit_log WHERE tenant_id = ? AND entity_table = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
