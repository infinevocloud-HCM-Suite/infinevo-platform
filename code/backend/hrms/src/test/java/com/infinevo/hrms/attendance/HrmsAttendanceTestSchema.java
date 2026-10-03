package com.infinevo.hrms.attendance;

import com.infinevo.hrms.project.HrmsProjectTestSchema;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Database schema and test fixture setup for HRMS attendance preferences integration tests (W-40.1).
 *
 * <p>Delegates database initialization and fixtures to {@link HrmsProjectTestSchema}.
 */
public final class HrmsAttendanceTestSchema {

    private HrmsAttendanceTestSchema() {}

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            new HrmsProjectTestSchema.Initializer().initialize(ctx);
        }
    }

    public static Connection migrationConnection() throws SQLException {
        return HrmsProjectTestSchema.migrationConnection();
    }

    public static Connection appConnection() throws SQLException {
        return HrmsProjectTestSchema.appConnection();
    }

    public static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        HrmsProjectTestSchema.bindTenant(conn, tenantId);
    }

    public static UUID insertTenant(String name) throws SQLException {
        return HrmsProjectTestSchema.insertTenant(name);
    }

    public static void insertMember(UUID tenantId, UUID userAccountId, String roleName) throws SQLException {
        HrmsProjectTestSchema.insertMember(tenantId, userAccountId, roleName);
    }

    public static UUID insertEmployee(UUID tenantId, String number) throws SQLException {
        return HrmsProjectTestSchema.insertEmployee(tenantId, number);
    }

    public static void insertMemberWithActions(UUID tenantId, UUID sub, String... actionCodes) throws SQLException {
        HrmsProjectTestSchema.insertMemberWithActions(tenantId, sub, actionCodes);
    }

    public static int countAuditRows(UUID tenantId, String entityTable) throws SQLException {
        return HrmsProjectTestSchema.countAuditRows(tenantId, entityTable);
    }

    public static void insertAdminAttendance(UUID tenantId, UUID employeeId, java.time.LocalDate date, String status)
            throws SQLException {
        try (Connection conn = migrationConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.attendance (tenant_id, employee_id, attendance_date, status, source, created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, 'ADMIN', 'admin', 'admin') "
                                + "ON CONFLICT (tenant_id, employee_id, attendance_date) DO UPDATE SET status = EXCLUDED.status, source = 'ADMIN'")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setObject(3, java.sql.Date.valueOf(date));
            ps.setString(4, status);
            ps.executeUpdate();
        }
    }

    public static AttendanceRow getAttendance(UUID tenantId, UUID employeeId, java.time.LocalDate date)
            throws SQLException {
        try (Connection conn = migrationConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement(
                        "SELECT status, source FROM core.attendance WHERE tenant_id = ? AND employee_id = ? AND attendance_date = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setObject(3, java.sql.Date.valueOf(date));
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new AttendanceRow(rs.getString("status"), rs.getString("source"));
                }
                return null;
            }
        }
    }

    public record AttendanceRow(String status, String source) {}
}
