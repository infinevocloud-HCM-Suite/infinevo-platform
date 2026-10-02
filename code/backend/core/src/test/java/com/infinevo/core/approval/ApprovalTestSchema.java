package com.infinevo.core.approval;

import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Schema management and raw JDBC helpers for approval integration tests (W-15.1, W-15.2).
 */
public final class ApprovalTestSchema {

    public static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private ApprovalTestSchema() {}

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
            if (!columnExists(conn, "tenant", "country_code")) {
                executeResource(conn, "db/migration/core/V033__tenant_locale_columns.sql");
            }
            if (!tableExists(conn, "employee")) {
                executeResource(conn, "db/migration/core/V010__employee.sql");
            }
            // The Employee entity maps the V014 org columns and the V026 login link. Whichever
            // test schema runs first creates core.employee for every IT sharing the container,
            // so each one has to bring the table up to what the entity reads.
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
            if (!tableExists(conn, "user_account")) {
                executeResource(conn, "db/migration/core/V009__user_account.sql");
            }
            if (!columnExists(conn, "employee", "user_account_id")) {
                executeResource(conn, "db/migration/core/V026__employee_user_account.sql");
            }
            if (!tableExists(conn, "reporting_line")) {
                executeResource(conn, "db/migration/core/V028__reporting_line.sql");
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
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, empId);
            ps.setObject(2, tenantId);
            ps.setString(3, employeeNumber);
            ps.setString(4, firstName);
            ps.setDate(5, Date.valueOf(LocalDate.of(2024, 1, 1)));
            ps.setString(6, "ACTIVE");
            ps.setString(7, email);
            ps.executeUpdate();
        }
        return empId;
    }

    public static void insertReportingLine(UUID tenantId, UUID employeeId, UUID managerId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.reporting_line (tenant_id, employee_id, manager_id, kind, effective_from) "
                                + "VALUES (?, ?, ?, 'PRIMARY', ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, employeeId);
            ps.setObject(3, managerId);
            ps.setDate(4, Date.valueOf(LocalDate.of(2024, 1, 1)));
            ps.executeUpdate();
        }
    }

    public static void clearDefinitions() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            if (tableExists(conn, "approval_delegation")) {
                stmt.execute("DELETE FROM core.approval_delegation");
            }
            if (tableExists(conn, "approval_step")) {
                stmt.execute("DELETE FROM core.approval_step");
            }
            if (tableExists(conn, "approval_instance")) {
                stmt.execute("DELETE FROM core.approval_instance");
            }
            if (tableExists(conn, "approval_definition")) {
                stmt.execute("DELETE FROM core.approval_definition");
            }
        }
    }

    public static void clearAll() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            if (tableExists(conn, "approval_delegation")) {
                stmt.execute("DELETE FROM core.approval_delegation");
            }
            if (tableExists(conn, "approval_step")) {
                stmt.execute("DELETE FROM core.approval_step");
            }
            if (tableExists(conn, "approval_instance")) {
                stmt.execute("DELETE FROM core.approval_instance");
            }
            if (tableExists(conn, "approval_definition")) {
                stmt.execute("DELETE FROM core.approval_definition");
            }
            if (tableExists(conn, "reporting_line")) {
                stmt.execute("DELETE FROM core.reporting_line");
            }
            if (tableExists(conn, "employee")) {
                stmt.execute("DELETE FROM core.employee");
            }
        }
    }

    public static int visibleRowCount(UUID tenantId) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.approval_definition")) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                conn.rollback();
            }
        }
    }

    public static int visibleStepCount(UUID tenantId) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.approval_step")) {
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
                """
                SELECT 1 FROM information_schema.columns
                 WHERE table_schema = 'core' AND table_name = ? AND column_name = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void executeResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = ApprovalTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
