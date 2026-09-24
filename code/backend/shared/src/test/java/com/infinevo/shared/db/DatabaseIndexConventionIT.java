package com.infinevo.shared.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-55 — Automated PostgreSQL catalog index convention integration test (PLAT-06, DEBT-018).
 *
 * <p>Validates that:
 * <ul>
 *   <li>100% of secondary indexes in tenant-scoped schemas ({@code core}, {@code hrms}, {@code payroll})
 *       have {@code tenant_id} as their leading column (column position 1).</li>
 *   <li>Primary keys ({@code _pkey}) and the un-scoped {@code reference} schema are appropriately exempt.</li>
 *   <li>Composite indexes adhere to naming convention {@code idx_<table/feature>_tenant_<columns>} or {@code uk_*}.</li>
 *   <li>High-growth table indexes and soft-delete covering indexes from {@code V024} exist in the catalog.</li>
 *   <li>A non-compliant index (violating tenant-leading rule) is detected and rejected by the assertion logic.</li>
 * </ul>
 */
@SpringBootTest(classes = DatabaseIndexConventionIT.TestApp.class)
class DatabaseIndexConventionIT extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {}

    record IndexInfo(
            String schemaName,
            String tableName,
            String indexName,
            String firstColumnName,
            boolean isPrimary,
            boolean isUnique,
            String indexDef) {}

    @BeforeAll
    static void applyMigrations() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {

            // Apply core migrations if not already present
            applyScriptIfMissing(conn, "core", "tenant", "db/migration/core/V001__tenant.sql");
            applyScriptIfMissing(conn, "core", "user_tenant", "db/migration/core/V002__user_tenant.sql");
            applyScriptIfMissing(conn, "core", "job_status", "db/migration/core/V006__job_status_and_shedlock.sql");
            applyScriptIfMissing(conn, "core", "audit_log", "db/migration/core/V008__audit_log.sql");
            applyScriptIfMissing(conn, "core", "user_account", "db/migration/core/V009__user_account.sql");
            applyScriptIfMissing(conn, "core", "employee", "db/migration/core/V010__employee.sql");

            // Apply V024 index optimizations
            if (!indexExists(conn, "core", "idx_employee_tenant_active_status")) {
                executeSqlResource(conn, "db/migration/core/V024__index_standard_optimizations.sql");
            }
        }
    }

    static List<String> findIndexViolations(List<IndexInfo> secondaryIndexes) {
        List<String> violations = new ArrayList<>();
        for (IndexInfo idx : secondaryIndexes) {
            // ShedLock table is a cluster coordination lock table with no tenant_id column
            if ("shedlock".equalsIgnoreCase(idx.tableName())) {
                continue;
            }

            // Exclude platform-level tenant metadata table index
            if ("tenant".equalsIgnoreCase(idx.tableName())) {
                continue;
            }

            // Explicit platform-level operational lookup exemptions:
            // 1. user_tenant(user_id) for initial tenant discovery before tenant context exists
            if ("user_tenant".equalsIgnoreCase(idx.tableName())
                    && ("idx_user_tenant_user_id".equalsIgnoreCase(idx.indexName())
                            || "idx_user_tenant_unique".equalsIgnoreCase(idx.indexName()))) {
                continue;
            }

            // 2. job_status(created_at) for global queue reaper daemon
            if ("job_status".equalsIgnoreCase(idx.tableName())
                    && "idx_job_status_created".equalsIgnoreCase(idx.indexName())) {
                continue;
            }

            // Rule 1: Leading column must be tenant_id
            if (!"tenant_id".equalsIgnoreCase(idx.firstColumnName())) {
                violations.add(String.format(
                        "Index %s.%s ON %s has leading column '%s', expected 'tenant_id'",
                        idx.schemaName(), idx.indexName(), idx.tableName(), idx.firstColumnName()));
            }

            // Rule 2: Naming convention enforcement
            // Composite / secondary indexes must follow idx_<table/feature>_tenant_<columns> or uk_*
            boolean validNaming =
                    (idx.indexName().startsWith("idx_") || idx.indexName().startsWith("uk_"))
                            && idx.indexName().contains("tenant");
            if (!validNaming) {
                violations.add(String.format(
                        "Index %s.%s ON %s does not follow naming convention (expected idx_*_tenant_* or uk_*_tenant_*)",
                        idx.schemaName(), idx.indexName(), idx.tableName()));
            }
        }
        return violations;
    }

    @Test
    @DisplayName(
            "Every secondary index in tenant schemas has tenant_id as leading column and follows naming convention (DEBT-018)")
    void everySecondaryIndexLeadsWithTenantId() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD)) {

            List<IndexInfo> secondaryIndexes = querySecondaryIndexes(conn, List.of("core", "hrms", "payroll"));
            assertThat(secondaryIndexes)
                    .as("Secondary indexes must exist in tenant schemas")
                    .isNotEmpty();

            List<String> violations = findIndexViolations(secondaryIndexes);

            assertThat(violations)
                    .as("All secondary indexes on tenant tables must lead with tenant_id and follow naming rules")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("V024 indexes for soft-delete status and job polling exist in PostgreSQL catalog")
    void v024IndexesExistInCatalog() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD)) {

            assertTrue(
                    indexExists(conn, "core", "idx_employee_tenant_active_status"),
                    "idx_employee_tenant_active_status must exist on core.employee");

            assertTrue(
                    indexExists(conn, "core", "idx_job_status_tenant_status"),
                    "idx_job_status_tenant_status must exist on core.job_status");
        }
    }

    @Test
    @DisplayName("Deliberate break: A secondary index violating conventions is caught by validation logic")
    void deliberateBreakIndexFailsVerification() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(
                        "CREATE TABLE core.break_test (id UUID PRIMARY KEY, tenant_id UUID NOT NULL, code VARCHAR(32))");
                // Deliberately create bad index where code is leading instead of tenant_id and name lacks tenant
                stmt.execute("CREATE INDEX idx_break_test_bad ON core.break_test (code, tenant_id)");

                List<IndexInfo> indexes = querySecondaryIndexes(conn, List.of("core"));
                List<String> violations = findIndexViolations(indexes);

                assertThat(violations)
                        .as("Validation logic must catch the non-tenant-leading index violation")
                        .anyMatch(v -> v.contains("idx_break_test_bad") && v.contains("has leading column 'code'"));
                assertThat(violations)
                        .as("Validation logic must catch the naming convention violation")
                        .anyMatch(v ->
                                v.contains("idx_break_test_bad") && v.contains("does not follow naming convention"));
            } finally {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DROP TABLE IF EXISTS core.break_test CASCADE");
                }
            }
        }
    }

    @Test
    @DisplayName("Unit test: findIndexViolations flags non-tenant-leading column")
    void unitTestFlagsNonTenantLeadingColumn() {
        IndexInfo badColumnIndex = new IndexInfo(
                "core", "employee", "idx_employee_tenant_test", "status", false, false, "CREATE INDEX ...");
        List<String> violations = findIndexViolations(List.of(badColumnIndex));
        assertThat(violations)
                .hasSize(1)
                .first()
                .asString()
                .contains("has leading column 'status', expected 'tenant_id'");
    }

    @Test
    @DisplayName("Unit test: findIndexViolations flags invalid naming convention")
    void unitTestFlagsInvalidIndexName() {
        IndexInfo badNameIndex =
                new IndexInfo("core", "employee", "custom_emp_idx", "tenant_id", false, false, "CREATE INDEX ...");
        List<String> violations = findIndexViolations(List.of(badNameIndex));
        assertThat(violations).hasSize(1).first().asString().contains("does not follow naming convention");
    }

    @Test
    @DisplayName("Unit test: findIndexViolations accepts valid tenant-leading index")
    void unitTestAcceptsValidIndex() {
        IndexInfo validIndex = new IndexInfo(
                "core", "employee", "idx_employee_tenant_status", "tenant_id", false, false, "CREATE INDEX ...");
        List<String> violations = findIndexViolations(List.of(validIndex));
        assertThat(violations).isEmpty();
    }

    private static List<IndexInfo> querySecondaryIndexes(Connection conn, List<String> schemas) throws SQLException {
        String sql =
                """
                SELECT
                    n.nspname AS schema_name,
                    t.relname AS table_name,
                    i.relname AS index_name,
                    a.attname AS first_column_name,
                    ix.indisprimary AS is_primary,
                    ix.indisunique AS is_unique,
                    pg_get_indexdef(ix.indexrelid) AS index_def
                FROM pg_index ix
                JOIN pg_class t ON t.oid = ix.indrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ix.indkey[0]
                WHERE n.nspname = ANY(?)
                  AND NOT ix.indisprimary
                ORDER BY n.nspname, t.relname, i.relname
                """;

        List<IndexInfo> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setArray(1, conn.createArrayOf("text", schemas.toArray()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new IndexInfo(
                            rs.getString("schema_name"),
                            rs.getString("table_name"),
                            rs.getString("index_name"),
                            rs.getString("first_column_name"),
                            rs.getBoolean("is_primary"),
                            rs.getBoolean("is_unique"),
                            rs.getString("index_def")));
                }
            }
        }
        return result;
    }

    private static boolean indexExists(Connection conn, String schema, String indexName) throws SQLException {
        String sql = "SELECT 1 FROM pg_indexes WHERE schemaname = ? AND indexname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean tableExists(Connection conn, String schema, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void applyScriptIfMissing(Connection conn, String schema, String table, String resourcePath)
            throws Exception {
        if (!tableExists(conn, schema, table)) {
            executeSqlResource(conn, resourcePath);
        }
    }

    private static void executeSqlResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = DatabaseIndexConventionIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }
}
