package com.infinevo.core.leave;

import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Schema management and raw JDBC helpers for leave integration tests (W-16.1).
 */
public final class LeaveTestSchema {

    public static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private LeaveTestSchema() {}

    public static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    public static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    public static void apply() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "tenant")) {
                executeResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "audit_log")) {
                executeResource(conn, "db/migration/core/V008__audit_log.sql");
            }
            if (!tableExists(conn, "employee")) {
                executeResource(conn, "db/migration/core/V010__employee.sql");
            }
            if (!tableExists(conn, "department")) {
                executeResource(conn, "db/migration/core/V011__department.sql");
            }
            if (!tableExists(conn, "designation")) {
                executeResource(conn, "db/migration/core/V012__designation.sql");
            }
            if (!tableExists(conn, "work_location")) {
                executeResource(conn, "db/migration/core/V013__work_location.sql");
            }
            if (!columnExists(conn, "employee", "department_id")) {
                executeResource(conn, "db/migration/core/V014__employee_org_columns.sql");
            }
            if (!tableExists(conn, "document")) {
                executeResource(conn, "db/migration/core/V037__document.sql");
            }
            if (!tableExists(conn, "approval_definition")) {
                executeResource(conn, "db/migration/core/V089__approval_definition.sql");
            }
            if (!tableExists(conn, "approval_instance")) {
                executeResource(conn, "db/migration/core/V090__approval_instance.sql");
            }
            if (!tableExists(conn, "approval_step")) {
                executeResource(conn, "db/migration/core/V091__approval_step.sql");
            }
            if (!tableExists(conn, "approval_delegation")) {
                executeResource(conn, "db/migration/core/V092__approval_delegation.sql");
            }
            if (!tableExists(conn, "leave_type")) {
                executeResource(conn, "db/migration/core/V110__leave_type.sql");
            }
            if (!tableExists(conn, "leave_policy")) {
                executeResource(conn, "db/migration/core/V111__leave_policy.sql");
            }
            if (!tableExists(conn, "leave_policy_eligibility")) {
                executeResource(conn, "db/migration/core/V112__leave_policy_eligibility.sql");
            }
            if (!tableExists(conn, "leave_allocation")) {
                executeResource(conn, "db/migration/core/V113__leave_allocation.sql");
            }
            if (!tableExists(conn, "leave_request")) {
                executeResource(conn, "db/migration/core/V114__leave_request.sql");
            }
            if (!tableExists(conn, "leave_request_document")) {
                executeResource(conn, "db/migration/core/V115__leave_request_document.sql");
            }
        }
    }

    public static void seedTenants() throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "Acme Manufacturing");
            ps.executeUpdate();
            ps.setObject(1, TENANT_B);
            ps.setString(2, "Globex Corporation");
            ps.executeUpdate();
        }
    }

    public static UUID insertEmployee(UUID tenantId, String employeeNumber, String firstName, String email)
            throws SQLException {
        UUID empId = UUID.randomUUID();
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, date_of_joining, status, work_email) "
                                + "VALUES (?, ?, ?, ?, '2024-01-01', 'ACTIVE', ?)")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenantId);
            ps.setString(3, employeeNumber);
            ps.setString(4, firstName);
            ps.setString(5, email);
            ps.executeUpdate();
        }
        return empId;
    }

    public static void clearAll() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            if (tableExists(conn, "leave_request_document")) {
                stmt.execute("DELETE FROM core.leave_request_document");
            }
            if (tableExists(conn, "leave_request")) {
                stmt.execute("DELETE FROM core.leave_request");
            }
            if (tableExists(conn, "approval_step")) {
                stmt.execute("DELETE FROM core.approval_step");
            }
            if (tableExists(conn, "approval_instance")) {
                stmt.execute("DELETE FROM core.approval_instance");
            }
            if (tableExists(conn, "approval_delegation")) {
                stmt.execute("DELETE FROM core.approval_delegation");
            }
            if (tableExists(conn, "approval_definition")) {
                stmt.execute("DELETE FROM core.approval_definition");
            }
            if (tableExists(conn, "leave_allocation")) {
                stmt.execute("DELETE FROM core.leave_allocation");
            }
            if (tableExists(conn, "leave_policy_eligibility")) {
                stmt.execute("DELETE FROM core.leave_policy_eligibility");
            }
            if (tableExists(conn, "leave_policy")) {
                stmt.execute("DELETE FROM core.leave_policy");
            }
            if (tableExists(conn, "leave_type")) {
                stmt.execute("DELETE FROM core.leave_type");
            }
            if (tableExists(conn, "document")) {
                stmt.execute("DELETE FROM core.document");
            }
            if (tableExists(conn, "employee")) {
                stmt.execute("DELETE FROM core.employee");
            }
            if (tableExists(conn, "audit_log")) {
                stmt.execute("DELETE FROM core.audit_log");
            }
        }
    }

    public static int visibleLeaveAllocationCount(UUID tenantId) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.leave_allocation")) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                conn.rollback();
            }
        }
    }

    public static int visibleLeaveTypeCount(UUID tenantId) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.leave_type")) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                conn.rollback();
            }
        }
    }

    public static int visibleLeavePolicyCount(UUID tenantId) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.leave_policy")) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                conn.rollback();
            }
        }
    }

    public static int visibleLeaveRequestCount(UUID tenantId) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.leave_request")) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                conn.rollback();
            }
        }
    }

    public static int visibleLeaveRequestDocumentCount(UUID tenantId) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.leave_request_document")) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                conn.rollback();
            }
        }
    }

    public static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement bind = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            bind.setString(1, tenantId.toString());
            bind.execute();
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM pg_tables WHERE schemaname = 'core' AND tablename = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = 'core' AND table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void executeResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = LeaveTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("migration not on the test classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
