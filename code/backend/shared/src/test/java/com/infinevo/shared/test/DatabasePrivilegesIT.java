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
 *   <li>{@code readonly_user} is refused write access on tenant schemas</li>
 *   <li>{@code migration_user} can execute DDL (CREATE and DROP tables)</li>
 *   <li>Platform schemas (core, hrms, payroll, reference) exist and are owned by {@code migration_user}</li>
 *   <li>Role attributes (NOSUPERUSER, NOBYPASSRLS) are enforced for all platform roles</li>
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
            conn.createStatement().execute("CREATE TABLE IF NOT EXISTS core.tenant (id VARCHAR(50))");
        }

        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection conn = DriverManager.getConnection(
                    jdbcUrl,
                    PostgresTestContainerInitializer.READONLY_USER,
                    PostgresTestContainerInitializer.READONLY_USER_PASSWORD)) {
                conn.createStatement().execute("INSERT INTO core.tenant VALUES ('t1')");
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
            ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT rolname, rolsuper, rolbypassrls FROM pg_roles "
                            + "WHERE rolname IN ('app_user', 'migration_user', 'readonly_user')");
            int count = 0;
            while (rs.next()) {
                count++;
                assertFalse(rs.getBoolean("rolsuper"), "Role " + rs.getString("rolname") + " must not be superuser");
                assertFalse(rs.getBoolean("rolbypassrls"), "Role " + rs.getString("rolname") + " must not bypass RLS");
            }
            assertEquals(3, count, "All 3 platform roles must exist");
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
                    .executeQuery("SELECT has_database_privilege('public', 'infinevo', 'CONNECT')");
            assertTrue(rs.next());
            assertFalse(rs.getBoolean(1), "PUBLIC must not have CONNECT privilege on infinevo database");
        }
    }
}
