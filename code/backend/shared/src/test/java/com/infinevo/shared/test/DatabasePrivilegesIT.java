package com.infinevo.shared.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-05 — Database privileges and security boundaries integration test.
 *
 * <p>Proves that:
 * <ul>
 *   <li>{@code app_user} is refused DDL execution</li>
 *   <li>{@code app_user} is refused write access on reference schema</li>
 *   <li>{@code app_user} CAN read and write tenant schemas, and CAN read reference (F-6)</li>
 *   <li>{@code worker_user} inherits {@code app_user}: it CAN read and write tenant schemas,
 *       is refused DDL, and is not {@code BYPASSRLS} (W-56)</li>
 *   <li>{@code readonly_user} CAN read every platform schema (F-6)</li>
 *   <li>{@code readonly_user} is refused write access on tenant schemas</li>
 *   <li>{@code migration_user} can execute DDL (CREATE and DROP tables)</li>
 *   <li>Platform schemas (core, hrms, payroll, reference) exist and are owned by {@code migration_user}</li>
 *   <li>Role attributes (NOSUPERUSER, NOBYPASSRLS) are enforced for all five platform roles</li>
 *   <li>PUBLIC role cannot connect to {@code infinevo} database</li>
 * </ul>
 */
@SpringBootTest(classes = DatabasePrivilegesIT.TestApp.class)
class DatabasePrivilegesIT extends AbstractIntegrationTest {

    @SpringBootApplication
    static class TestApp {
        // Minimal Spring Boot context for testing database privileges in shared module.
    }

    @Test
    @DisplayName("app_user is refused DDL execution")
    void appUserDeniedDdl() {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.APP_USER,
                    PostgresTestContainerInitializer.APP_USER_PASSWORD)) {
                conn.createStatement().execute("CREATE TABLE core.test_ddl (id INT)");
            }
        });
        assertEquals("42501", ex.getSQLState(), "SQLState must be 42501 (insufficient_privilege)");
    }

    @Test
    @DisplayName("app_user is refused write operations on reference schema")
    void appUserDeniedWriteOnReferenceSchema() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
            conn.createStatement()
                    .execute("CREATE TABLE IF NOT EXISTS reference.country (code VARCHAR(2), name VARCHAR(50))");
        }

        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.APP_USER,
                    PostgresTestContainerInitializer.APP_USER_PASSWORD)) {
                conn.createStatement().execute("INSERT INTO reference.country VALUES ('XX', 'Test Country')");
            }
        });
        assertEquals("42501", ex.getSQLState(), "SQLState must be 42501 (insufficient_privilege)");
    }

    @Test
    @DisplayName("readonly_user is refused write operations on tenant schema")
    void readonlyUserDeniedWriteOnTenantSchema() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS core.readonly_write_probe (id INT)");
        }

        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.READONLY_USER,
                    PostgresTestContainerInitializer.READONLY_USER_PASSWORD)) {
                conn.createStatement().execute("INSERT INTO core.readonly_write_probe VALUES (1)");
            }
        });
        assertEquals("42501", ex.getSQLState(), "SQLState must be 42501 (insufficient_privilege)");
    }

    @Test
    @DisplayName("migration_user can execute DDL (CREATE and DROP tables)")
    void migrationUserAllowedDdl() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
            conn.createStatement().execute("CREATE TABLE core.test_migration (id INT)");
            conn.createStatement().execute("DROP TABLE core.test_migration");
        }
    }

    @Test
    @DisplayName("Platform schemas exist and are owned by migration_user")
    void platformSchemasOwnedByMigrationUser() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
            ResultSet rs = conn.createStatement()
                    .executeQuery(
                            "SELECT nspname, pg_get_userbyid(nspowner) AS owner "
                                    + "FROM pg_namespace WHERE nspname IN ('core', 'hrms', 'payroll', 'reference') ORDER BY nspname");
            int count = 0;
            while (rs.next()) {
                count++;
                assertEquals(
                        "migration_user",
                        rs.getString("owner"),
                        "Schema " + rs.getString("nspname") + " should be owned by migration_user");
            }
            assertEquals(4, count, "All 4 schemas must exist");
        }
    }

    @Test
    @DisplayName("Platform roles have explicit NOSUPERUSER and NOBYPASSRLS attributes")
    void platformRoleAttributesDeclared() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
            // worker_user joined the set at W-56. app_user is unchanged by that ticket: it
            // keeps LOGIN and its password, and stays the role the application connects as.
            ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT rolname, rolsuper, rolbypassrls FROM pg_roles "
                            + "WHERE rolname IN ('app_user', 'worker_user', 'migration_user', "
                            + "'readonly_user', 'keycloak_user')");
            int count = 0;
            while (rs.next()) {
                count++;
                assertFalse(rs.getBoolean("rolsuper"), "Role " + rs.getString("rolname") + " must not be superuser");
                assertFalse(rs.getBoolean("rolbypassrls"), "Role " + rs.getString("rolname") + " must not bypass RLS");
            }
            assertEquals(5, count, "All 5 platform roles must exist");
        }
    }

    /**
     * The worker connects as its own role rather than sharing the application's credential,
     * but every grant and RLS policy is attached to {@code app_user}. That only holds while
     * the membership does, so assert it here — otherwise a missing {@code GRANT app_user TO
     * worker_user} surfaces as an unrelated privilege failure on the worker (W-56).
     */
    @Test
    @DisplayName("worker_user can log in and inherits app_user")
    void workerUserInheritsAppUser() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.WORKER_USER,
                PostgresTestContainerInitializer.WORKER_USER_PASSWORD)) {
            ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT pg_has_role(current_user, '" + PostgresTestContainerInitializer.APP_USER
                            + "', 'USAGE')");
            assertTrue(rs.next());
            assertTrue(rs.getBoolean(1), "worker_user must inherit app_user");
        }
    }

    /**
     * The grants reach {@code worker_user} only through its membership of {@code app_user}.
     * Asserting the membership (above) says the edge exists; this says it carries what the
     * worker needs. Both are kept — a grant copied onto {@code worker_user} directly would
     * pass this and still break the moment the copy drifts.
     */
    @Test
    @DisplayName("worker_user CAN insert, select, update and delete on every tenant schema")
    void workerUserAllowedDmlOnTenantSchemas() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        for (String schema : new String[] {"core", "hrms", "payroll"}) {
            String table = schema + ".worker_dml_probe";
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.MIGRATION_USER,
                    PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
                conn.createStatement().execute("CREATE TABLE IF NOT EXISTS " + table + " (id INT)");
            }
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.WORKER_USER,
                    PostgresTestContainerInitializer.WORKER_USER_PASSWORD)) {
                conn.createStatement().execute("INSERT INTO " + table + " VALUES (1)");
                conn.createStatement().execute("UPDATE " + table + " SET id = 2 WHERE id = 1");
                ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM " + table + " WHERE id = 2");
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "worker_user must be able to read back its own write in " + schema);
                conn.createStatement().execute("DELETE FROM " + table);
            }
        }
    }

    /** Inheritance carries the grants, and it must not carry DDL — app_user has none to give. */
    @Test
    @DisplayName("worker_user is refused DDL execution")
    void workerUserDeniedDdl() {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.WORKER_USER,
                    PostgresTestContainerInitializer.WORKER_USER_PASSWORD)) {
                conn.createStatement().execute("CREATE TABLE core.worker_test_ddl (id INT)");
            }
        });
        assertEquals("42501", ex.getSQLState(), "SQLState must be 42501 (insufficient_privilege)");
    }

    /**
     * Read from the catalogue for the connected role itself. Deliberate break 5 in the spec
     * is {@code ALTER ROLE worker_user WITH BYPASSRLS}: without this, the worker would see
     * across tenants and every RLS test in {@code migration} would still pass, because they
     * all connect as {@code app_user}.
     */
    @Test
    @DisplayName("worker_user is not BYPASSRLS")
    void workerUserIsNotBypassRls() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.WORKER_USER,
                PostgresTestContainerInitializer.WORKER_USER_PASSWORD)) {
            ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user");
            assertTrue(rs.next());
            assertFalse(rs.getBoolean("rolsuper"), "worker_user must not be a superuser");
            assertFalse(rs.getBoolean("rolbypassrls"), "worker_user must not bypass RLS");
        }
    }

    @Test
    @DisplayName("PUBLIC connect privilege on infinevo database is revoked")
    void publicConnectRevokedOnInfinevoDatabase() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
            ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT has_database_privilege('public', current_database(), 'CONNECT')");
            assertTrue(rs.next());
            assertFalse(rs.getBoolean(1), "PUBLIC must not have CONNECT privilege on infinevo database");
        }
    }

    /**
     * F-6 positive control. Without this, every refusal test still passes when the
     * ALTER DEFAULT PRIVILEGES block is deleted from 03-grants.sql - the boundary
     * would be "app_user can do nothing", which is not what W-07 needs.
     */
    @Test
    @DisplayName("app_user CAN insert, select, update and delete on every tenant schema")
    void appUserAllowedDmlOnTenantSchemas() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        for (String schema : new String[] {"core", "hrms", "payroll"}) {
            String table = schema + ".dml_probe";
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.MIGRATION_USER,
                    PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
                conn.createStatement().execute("CREATE TABLE IF NOT EXISTS " + table + " (id INT)");
            }
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.APP_USER,
                    PostgresTestContainerInitializer.APP_USER_PASSWORD)) {
                conn.createStatement().execute("INSERT INTO " + table + " VALUES (1)");
                conn.createStatement().execute("UPDATE " + table + " SET id = 2 WHERE id = 1");
                ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM " + table + " WHERE id = 2");
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "app_user must be able to read back its own write in " + schema);
                conn.createStatement().execute("DELETE FROM " + table);
            }
        }
    }

    /** F-6 positive control: reference is read-only to app_user, not unreadable. */
    @Test
    @DisplayName("app_user CAN read the reference schema")
    void appUserAllowedReadOnReferenceSchema() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS reference.read_probe (code VARCHAR(2))");
        }
        try (Connection conn = DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.APP_USER,
                PostgresTestContainerInitializer.APP_USER_PASSWORD)) {
            ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM reference.read_probe");
            assertTrue(rs.next(), "app_user must be able to SELECT from reference");
        }
    }

    /** F-6 positive control: readonly_user is read-ONLY, not read-nothing. */
    @Test
    @DisplayName("readonly_user CAN read every platform schema")
    void readonlyUserAllowedReadOnAllSchemas() throws SQLException {
        String jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();
        for (String schema : new String[] {"core", "hrms", "payroll", "reference"}) {
            String table = schema + ".ro_probe";
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.MIGRATION_USER,
                    PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)) {
                conn.createStatement().execute("CREATE TABLE IF NOT EXISTS " + table + " (id INT)");
            }
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.READONLY_USER,
                    PostgresTestContainerInitializer.READONLY_USER_PASSWORD)) {
                ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM " + table);
                assertTrue(rs.next(), "readonly_user must be able to SELECT from " + schema);
            }
        }
    }
}
