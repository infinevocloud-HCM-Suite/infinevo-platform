package com.infinevo.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W-06 — Integration test proving the migration mechanism.
 *
 * <p>Uses the shared Testcontainers container (provisioned with roles, schemas, and grants).
 * Runs Flyway against the test fixtures in {@code db/migration-test/} as {@code migration_user}.
 * The shipped script trees ({@code db/migration/}) stay empty — this proves the mechanism,
 * not the data model.
 *
 * <p>Fixtures: V001 (reference), V002 (core), V003 (hrms), V004 (payroll).
 * All are destroyed with the container after the test run.
 */
@EnabledIfDockerAvailable
class FlywayMigrationIT {

    private static String jdbcUrl;
    private static Flyway flyway;

    @BeforeAll
    static void runMigrations() {
        jdbcUrl = PostgresTestContainerInitializer.getJdbcUrl();

        flyway = Flyway.configure()
                .dataSource(
                        jdbcUrl,
                        PostgresTestContainerInitializer.MIGRATION_USER,
                        PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD)
                .defaultSchema("migration")
                .schemas("migration", "reference", "core", "hrms", "payroll")
                .locations(
                        "classpath:db/migration-test/reference",
                        "classpath:db/migration-test/core",
                        "classpath:db/migration-test/hrms",
                        "classpath:db/migration-test/payroll")
                .createSchemas(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .failOnMissingLocations(true)
                .load();

        flyway.migrate();
    }

    // ── §6 criterion 1: flyway_schema_history exists, in migration schema, owned by migration_user

    @Test
    void historyTableExistsInMigrationSchemaOwnedByMigrationUser() throws SQLException {
        try (Connection conn = adminConnection();
                ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT count(*), coalesce(string_agg(schemaname||'/'||tableowner, ','), 'NONE')"
                                + " FROM pg_tables WHERE tablename = 'flyway_schema_history'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
            assertThat(rs.getString(2)).isEqualTo("migration/migration_user");
        }
    }

    // ── §6 criterion: all four fixture tables owned by migration_user

    @Test
    void allFixtureTablesOwnedByMigrationUser() throws SQLException {
        try (Connection conn = adminConnection();
                ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT schemaname, tablename, tableowner FROM pg_tables"
                                + " WHERE tablename IN ('country_fixture','tenant_fixture','hrms_fixture','payroll_fixture')"
                                + " ORDER BY schemaname, tablename")) {
            int count = 0;
            while (rs.next()) {
                assertThat(rs.getString("tableowner"))
                        .as("table %s.%s", rs.getString("schemaname"), rs.getString("tablename"))
                        .isEqualTo(PostgresTestContainerInitializer.MIGRATION_USER);
                count++;
            }
            assertThat(count).as("all four fixture tables present").isEqualTo(4);
        }
    }

    // ── §6 criterion: records applied migrations (all SUCCESS)

    @Test
    void allMigrationsAppliedSuccessfully() {
        MigrationInfo[] infos = flyway.info().applied();
        assertThat(infos).hasSizeGreaterThanOrEqualTo(4);
        for (MigrationInfo info : infos) {
            assertThat(info.getState())
                    .as("migration %s", info.getDescription())
                    .isEqualTo(MigrationState.SUCCESS);
        }
    }

    // ── §12 item 13: cross-schema ordering proved by FK resolving

    @Test
    void crossSchemaForeignKeyResolves() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            conn.createStatement()
                    .execute(
                            "INSERT INTO reference.country_fixture (code, name) VALUES ('IN', 'India') ON CONFLICT (code) DO NOTHING");
            conn.createStatement()
                    .execute(
                            "INSERT INTO core.tenant_fixture (tenant_id, country_code) VALUES (gen_random_uuid(), 'IN')");
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM core.tenant_fixture")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
            }
        }
    }

    // ── §12 item 12: app_user can SELECT/INSERT/UPDATE/DELETE core fixture (ALTER DEFAULT PRIVILEGES chain)

    @Test
    void appUserCanDmlCoreFixture() throws SQLException {
        try (Connection conn = appUserConnection()) {
            conn.createStatement()
                    .execute(
                            "INSERT INTO core.tenant_fixture (tenant_id, country_code) VALUES (gen_random_uuid(), 'IN')");
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM core.tenant_fixture")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
            }
        }
    }

    // ── §12 item 12: app_user reads but cannot write in reference

    @Test
    void appUserCanReadReferenceButNotWrite() throws SQLException {
        try (Connection conn = appUserConnection()) {
            // read must succeed
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM reference.country_fixture")) {
                assertThat(rs.next()).isTrue();
            }
            // write must be refused
            assertThatThrownBy(() -> conn.createStatement()
                            .execute("INSERT INTO reference.country_fixture (code, name) VALUES ('XX', 'Test')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    // ── §12 item 12: app_user has no USAGE on migration schema

    @Test
    void appUserHasNoUsageOnMigrationSchema() throws SQLException {
        try (Connection conn = adminConnection();
                ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT has_schema_privilege('app_user','migration','USAGE')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isFalse();
        }
    }

    // ── §12 item 12: readonly_user reads but cannot write

    @Test
    void readonlyUserCanReadButNotWrite() throws SQLException {
        try (Connection conn = readonlyUserConnection()) {
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM core.tenant_fixture")) {
                assertThat(rs.next()).isTrue();
            }
            assertThatThrownBy(
                            () -> conn.createStatement()
                                    .execute(
                                            "INSERT INTO core.tenant_fixture (tenant_id, country_code) VALUES (gen_random_uuid(), 'IN')"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    // ── §8 break 6: re-running applies nothing (idempotency)

    @Test
    void reRunningMigratesNothing() {
        int applied = flyway.migrate().migrationsExecuted;
        assertThat(applied).isZero();
    }

    // ── §8 break 1 shape: app_user cannot connect to migration schema (no USAGE)

    @Test
    void appUserCannotQueryHistoryTable() throws SQLException {
        try (Connection conn = appUserConnection()) {
            assertThatThrownBy(() ->
                            conn.createStatement().executeQuery("SELECT count(*) FROM migration.flyway_schema_history"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    // ── helpers

    private static Connection adminConnection() throws SQLException {
        // postgres superuser from the Testcontainer
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    private static Connection migrationUserConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    private static Connection appUserConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl, PostgresTestContainerInitializer.APP_USER, PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }

    private static Connection readonlyUserConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.READONLY_USER,
                PostgresTestContainerInitializer.READONLY_USER_PASSWORD);
    }
}
