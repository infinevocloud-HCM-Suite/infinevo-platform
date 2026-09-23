package com.infinevo.core.employee;

import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Schema, seed data and owner-level reads shared by the employee integration tests (W-13.1).
 *
 * <p>The shipped migrations are applied as written — {@code V001__tenant.sql} and
 * {@code V010__employee.sql} — so the table under test is the migrated one and not a copy that has
 * drifted from it. Both are idempotent here because the Postgres container is shared by every
 * integration test in the module.
 *
 * <p>Two connections, and the difference is the whole point of these tests.
 * {@link #migrationConnection()} is the schema owner: it bypasses row-level security and is how a
 * row gets planted and inspected. {@link #appConnection()} is {@code app_user}, the role the
 * application actually connects as, which holds no {@code BYPASSRLS} — anything read through it has
 * passed the policy at {@code V010__employee.sql:55-62}.
 */
final class EmployeeTestSchema {

    /** Acme Manufacturing — {@code infra/docker/seed/01-tenants.sql}. */
    static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Globex Corporation — {@code infra/docker/seed/01-tenants.sql}. */
    static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private EmployeeTestSchema() {}

    static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    /** Applies the shipped migrations this feature needs. Idempotent — other suites share the container. */
    static void apply() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "tenant")) {
                executeResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "employee")) {
                executeResource(conn, "db/migration/core/V010__employee.sql");
            }
        }
    }

    /** Seeds the two dev tenants. Idempotent. */
    static void seedTenants() throws SQLException {
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

    /**
     * Inserts an employee directly, as the schema owner, so row-level security can be tested against
     * a row the application under test never created.
     *
     * @return the generated id
     */
    static UUID seedEmployee(UUID tenantId, String employeeNumber, String firstName) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (tenant_id, employee_number, first_name, date_of_joining, status, created_by, updated_by)
                        VALUES (?, ?, ?, ?, 'ACTIVE', 'test', 'test')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, employeeNumber);
            ps.setString(3, firstName);
            ps.setObject(4, LocalDate.of(2026, 4, 1));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** Removes every employee row, as the schema owner. For {@code @BeforeEach} and {@code @AfterAll}. */
    static void clearEmployees() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM core.employee");
        }
    }

    /** Counts employee rows for one tenant, bypassing row-level security — the control for the assertions. */
    static int countEmployees(UUID tenantId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.employee WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** One column of one employee row, read as the schema owner. */
    static Object readColumn(UUID id, String column) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT " + column + " FROM core.employee WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
            }
        }
    }

    /**
     * Whether {@code app_user} can see one specific row with {@code tenantId} bound — the hard read
     * spec section 9 asks for.
     *
     * <p>It has to be this and not a call through the service: the service filters soft-deleted rows
     * in Java, so a service-level "not found" would look identical whether the policy works or not.
     */
    static boolean visibleToAppUser(UUID tenantId, UUID employeeId) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.employee WHERE id = ?")) {
                    ps.setObject(1, employeeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }

    /** How many rows {@code app_user} can see at all with {@code tenantId} bound. */
    static int visibleRowCount(UUID tenantId) throws SQLException {
        try (Connection conn = appConnection()) {
            conn.setAutoCommit(false);
            try {
                bindTenant(conn, tenantId);
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM core.employee")) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                conn.rollback();
            }
        }
    }

    private static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
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

    private static void executeResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = EmployeeTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
