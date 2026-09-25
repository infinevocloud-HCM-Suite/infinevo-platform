package com.infinevo.core.document;

import com.infinevo.shared.test.AzuriteTestContainer;
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
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The database, storage and fixtures behind the document store's integration tests (W-21).
 *
 * <p>A database of its own, {@value #DATABASE}, for the reason {@code AuthzTestSchema} gives: the
 * guard test needs the role tables, which the shared database's other tests do not create, and
 * building them there would couple test classes that happen to share a JVM.
 *
 * <p>The shipped migration scripts are run as {@code migration_user}, the owner — the same scripts
 * Flyway runs, off the test classpath ({@code shared/pom.xml} copies them there).
 */
final class DocumentTestSchema {

    static final String DATABASE = "infinevo_document";

    /** Test-only; long enough for {@link DocumentLinkServiceImpl#MIN_SECRET_LENGTH}. */
    static final String LINK_SECRET = "integration-test-document-link-secret";

    private static String jdbcUrl;

    private DocumentTestSchema() {}

    /**
     * Points the context at {@value #DATABASE}, at the shared Azurite container, and gives it a link
     * secret. Runs after {@code PostgresTestContainerInitializer}, whose datasource URL it replaces.
     */
    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext ctx) {
            TestPropertyValues.of(
                            "spring.datasource.url=" + jdbcUrl(),
                            "document.link.secret=" + LINK_SECRET,
                            "document.blob.connection-string=" + AzuriteTestContainer.connectionString())
                    .applyTo(ctx.getEnvironment());
        }
    }

    static synchronized String jdbcUrl() {
        if (jdbcUrl == null) {
            String url = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
            try (Connection conn = DriverManager.getConnection(
                    url,
                    PostgresTestContainerInitializer.MIGRATION_USER,
                    PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
                if (!tableExists(conn, "document")) {
                    // Identity and membership, for the tenant filter and the permission check.
                    executeResource(conn, "db/migration/core/V001__tenant.sql");
                    executeResource(conn, "db/migration/core/V002__user_tenant.sql");
                    executeResource(conn, "db/migration/core/V009__user_account.sql");
                    // The employee a document may belong to, with the V014 org columns its entity maps.
                    executeResource(conn, "db/migration/core/V010__employee.sql");
                    executeResource(conn, "db/migration/core/V011__department.sql");
                    executeResource(conn, "db/migration/core/V012__designation.sql");
                    executeResource(conn, "db/migration/core/V013__work_location.sql");
                    executeResource(conn, "db/migration/core/V014__employee_org_columns.sql");
                    // The catalogue and roles, corrected by V025 — which adds the core.document.* codes.
                    executeResource(conn, "db/migration/reference/V020__action.sql");
                    executeResource(conn, "db/migration/core/V021__role.sql");
                    executeResource(conn, "db/migration/core/V022__role_action.sql");
                    executeResource(conn, "db/migration/core/V023__user_role.sql");
                    executeResource(conn, "db/migration/core/V025__catalogue_correction.sql");
                    executeResource(conn, "db/migration/core/V037__document.sql");
                }
            } catch (Exception e) {
                throw new IllegalStateException("Could not prepare " + DATABASE, e);
            }
            jdbcUrl = url;
        }
        return jdbcUrl;
    }

    static Connection migrationConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    /** A fresh tenant. The {@code V022} trigger seeds its seven system roles. */
    static UUID insertTenant(String name) throws SQLException {
        UUID tenantId = UUID.randomUUID();
        try (Connection conn = migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
        return tenantId;
    }

    /**
     * A member of the tenant — the {@code core.user_tenant} row the tenant filter checks and the
     * {@code core.user_account} row the permission check resolves — holding one system role.
     */
    static void insertMember(UUID tenantId, UUID keycloakUserId, String roleCode) throws SQLException {
        try (Connection conn = migrationConnection()) {
            try (PreparedStatement ps =
                    conn.prepareStatement("INSERT INTO core.user_tenant (tenant_id, user_id) VALUES (?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, keycloakUserId);
                ps.executeUpdate();
            }
            UUID accountId;
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.user_account (tenant_id, keycloak_user_id, email, created_by, updated_by)
                    VALUES (?, ?, ?, 'test', 'test')
                    RETURNING id
                    """)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, keycloakUserId);
                ps.setString(3, keycloakUserId + "@document.test");
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    accountId = rs.getObject(1, UUID.class);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO core.user_role (tenant_id, user_account_id, role_id)
                    SELECT ?, ?, r.id FROM core.role r WHERE r.tenant_id = ? AND r.code = ?
                    """)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, accountId);
                ps.setObject(3, tenantId);
                ps.setString(4, roleCode);
                if (ps.executeUpdate() != 1) {
                    throw new IllegalStateException("no role " + roleCode + " in tenant " + tenantId);
                }
            }
        }
    }

    static UUID insertEmployee(UUID tenantId, String number) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee (tenant_id, employee_number, first_name, date_of_joining, status)
                        VALUES (?, ?, 'Test', DATE '2026-04-01', 'ACTIVE')
                        RETURNING id
                        """)) {
            ps.setObject(1, tenantId);
            ps.setString(2, number);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    /** A document row written directly as the owner, with no blob — for the row-level tests. */
    static UUID insertDocumentRow(UUID tenantId, DocumentKind kind) throws SQLException {
        return insertDocumentRow(tenantId, kind, null);
    }

    /** As above, filed against {@code employeeId} — null for a tenant-level document. */
    static UUID insertDocumentRow(UUID tenantId, DocumentKind kind, UUID employeeId) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.document (id, tenant_id, employee_id, kind, file_name, content_type,
                                                   size_bytes, blob_container, blob_path, checksum_sha256)
                        VALUES (?, ?, ?, ?, 'row.csv', 'text/csv', 3, 'documents', ?, ?)
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, employeeId);
            ps.setString(4, kind.name());
            ps.setString(5, DocumentServiceImpl.blobPath(tenantId, employeeId, kind, id));
            ps.setString(6, "0".repeat(64));
            ps.executeUpdate();
        }
        return id;
    }

    /** The raw row, read as the owner so row-level security does not hide it. */
    static boolean rowExists(UUID documentId) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.document WHERE id = ?")) {
            ps.setObject(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    static Object readColumn(UUID documentId, String column) throws SQLException {
        try (Connection conn = migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT " + column + " FROM core.document WHERE id = ?")) {
            ps.setObject(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
            }
        }
    }

    static void bindTenant(Connection conn, UUID tenantId) throws SQLException {
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
        try (InputStream is = DocumentTestSchema.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
