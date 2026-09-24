package com.infinevo.shared.audit;

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
 * Schema setup shared by the audit integration tests.
 *
 * <p>The shipped {@code V001__tenant.sql} and {@code V008__audit_log.sql} are applied as written
 * — the table under test is the migrated one, not a copy — plus {@code core.audited_probe}, the
 * test-only table behind {@link AuditedProbe}, which is built here with {@code tenant_id} and the
 * same row-level security policy every business table carries.
 */
final class AuditTestSchema {

    static final UUID TENANT_A = UUID.fromString("a0000000-0000-4000-8000-00000000000a");
    static final UUID TENANT_B = UUID.fromString("b0000000-0000-4000-8000-00000000000b");

    private AuditTestSchema() {}

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

    /** Applies the shipped migrations this feature needs, then the probe table. Idempotent. */
    static void apply() throws Exception {
        try (Connection conn = migrationConnection()) {
            if (!tableExists(conn, "tenant")) {
                executeResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "audit_log")) {
                executeResource(conn, "db/migration/core/V008__audit_log.sql");
            }
            if (!tableExists(conn, "audited_probe")) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(
                            """
                            CREATE TABLE core.audited_probe (
                                id UUID PRIMARY KEY,
                                tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
                                name VARCHAR(100),
                                amount NUMERIC(19,4),
                                api_token VARCHAR(100),
                                address_line1 VARCHAR(200),
                                zip_code VARCHAR(12)
                            )
                            """);
                    stmt.execute("ALTER TABLE core.audited_probe ENABLE ROW LEVEL SECURITY");
                    stmt.execute(
                            """
                            CREATE POLICY tenant_isolation ON core.audited_probe
                                USING (
                                  tenant_id = CASE
                                    WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
                                    WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
                                    ELSE current_setting('app.current_tenant_id', true)::uuid
                                  END
                                )
                            """);
                }
            }
        }
    }

    /** Seeds the two tenants the audit tests use. Idempotent. */
    static void seedTenants() throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "Audit Tenant A");
            ps.executeUpdate();
            ps.setObject(1, TENANT_B);
            ps.setString(2, "Audit Tenant B");
            ps.executeUpdate();
        }
    }

    /**
     * Clears the audit rows the tests wrote, and nothing else, so a test can insert a probe and
     * then start counting from zero. Runs as the schema owner, so row-level security cannot hide
     * a row the assertion is about to count.
     */
    static void clearAudit() throws SQLException {
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM core.audit_log WHERE tenant_id IN ('" + TENANT_A + "','" + TENANT_B + "')");
        }
    }

    /** Clears audit rows and probe rows both. For {@code @BeforeEach}, not for mid-test. */
    static void clearAll() throws SQLException {
        clearAudit();
        try (Connection conn = migrationConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM core.audited_probe WHERE tenant_id IN ('" + TENANT_A + "','" + TENANT_B + "')");
        }
    }

    /** Counts audit rows for one tenant, bypassing RLS by connecting as the schema owner. */
    static int countAuditRows(UUID tenantId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM core.audit_log WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * The whole audit row as text, straight from the database as the schema owner. Used to prove
     * a redacted value is absent from <em>every</em> column, not merely from the map the query
     * service hands back — W-13.2 spec section 2.
     */
    static String rawAuditRowText(UUID tenantId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT audit_log::text FROM core.audit_log audit_log WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            StringBuilder text = new StringBuilder();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    text.append(rs.getString(1)).append(System.lineSeparator());
                }
            }
            return text.toString();
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
        try (InputStream is = AuditTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
