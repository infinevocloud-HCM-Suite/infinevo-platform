package com.infinevo.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * #136 — Runs the migration scripts that actually ship.
 *
 * <p>{@link FlywayMigrationIT} proves the <em>mechanism</em>: it overrides {@code
 * spring.flyway.locations} to point at {@code db/migration-test/} fixtures, so nothing under {@code
 * db/migration/} is ever executed by Flyway. {@link TenantIsolationIT} reads {@code
 * V001__tenant.sql} off the classpath and executes it over plain JDBC, which skips Flyway
 * altogether — no version ordering, no checksum, no history row — and never touches {@code
 * V002__user_tenant.sql} at all. A shipped script could therefore be unparseable, misnumbered, or
 * depend on an object no earlier script creates, and the suite would stay green.
 *
 * <p>This test boots {@link MigrationApplication} with <strong>no locations override</strong>, so
 * Flyway reads the four shipped directories named in {@code application.yml} and applies what is
 * there, in order, as {@code migration_user}. It runs against a database of its own
 * ({@code infinevo_shipped}) because the shared one carries the fixtures and {@code
 * TenantIsolationIT}'s hand-applied table, both of which would collide.
 */
@EnabledIfDockerAvailable
class ShippedMigrationsIT {

    /** Its own database: the default one carries fixture versions that collide with the shipped ones. */
    private static final String DATABASE = "infinevo_shipped";

    private static String jdbcUrl;
    private static Flyway flyway;

    @BeforeAll
    static void applyShippedMigrations() {
        jdbcUrl = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);

        // No --spring.flyway.locations. That omission is the test: Flyway resolves the four
        // shipped directories from application.yml and applies whatever is committed there.
        ConfigurableApplicationContext context = MigrationApplication.launch(
                "--DB_URL=" + jdbcUrl,
                "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
                "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);

        flyway = context.getBean(Flyway.class);
    }

    // ── every committed script ran, and Flyway recorded it

    @Test
    void everyShippedScriptIsAppliedAndSuccessful() {
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied)
                .as("shipped migrations recorded in flyway_schema_history")
                .isNotEmpty();
        for (MigrationInfo info : applied) {
            assertThat(info.getState())
                    .as("state of %s %s", info.getVersion(), info.getDescription())
                    .isEqualTo(MigrationState.SUCCESS);
        }
    }

    /**
     * The count on disk and the count in the history table must agree. An unapplied script is the
     * failure #136 describes: committed, shipped, and never executed by anything.
     */
    @Test
    void historyCountMatchesTheNumberOfCommittedScripts() {
        assertThat(flyway.info().pending())
                .as("scripts on the classpath that Flyway has not applied")
                .isEmpty();
        assertThat(flyway.info().applied())
                .as("applied count must equal the committed script count")
                .hasSize(flyway.info().all().length);
    }

    /** Checksums are recorded, so a later edit to a shipped script is caught rather than ignored. */
    @Test
    void everyAppliedScriptHasARecordedChecksum() {
        for (MigrationInfo info : flyway.info().applied()) {
            assertThat(info.getChecksum())
                    .as("checksum of %s %s", info.getVersion(), info.getDescription())
                    .isNotNull();
        }
    }

    /** Re-running the shipped tree applies nothing and does not fail validation. */
    @Test
    void reRunningTheShippedTreeAppliesNothing() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    // ── the objects the scripts claim to create

    @Test
    void everyTableCreatedByAShippedScriptIsOwnedByMigrationUser() throws SQLException {
        List<String> tables = shippedTables();
        assertThat(tables).as("tables created outside the migration schema").isNotEmpty();
        try (Connection conn = migrationUserConnection()) {
            for (String qualified : tables) {
                String[] parts = qualified.split("\\.", 2);
                try (ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT tableowner FROM pg_tables WHERE schemaname = '" + parts[0]
                                + "' AND tablename = '" + parts[1] + "'")) {
                    assertThat(rs.next()).as("%s exists", qualified).isTrue();
                    assertThat(rs.getString(1))
                            .as("owner of %s", qualified)
                            .isEqualTo(PostgresTestContainerInitializer.MIGRATION_USER);
                }
            }
        }
    }

    /**
     * W-07's tenant_id standard, asserted against the applied database rather than the script text.
     * The CI gates read the SQL; this reads what Postgres actually built from it.
     */
    @Test
    void everyTenantScopedTableHasTenantIdAndRowLevelSecurity() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            for (String qualified : shippedTables()) {
                String[] parts = qualified.split("\\.", 2);
                if ("reference".equals(parts[0])) {
                    continue; // D-08: the reference schema is exempt, by design
                }
                try (ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT count(*) FROM information_schema.columns"
                                + " WHERE table_schema = '" + parts[0] + "' AND table_name = '" + parts[1]
                                + "' AND column_name = 'tenant_id'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1))
                            .as("tenant_id column on %s", qualified)
                            .isEqualTo(1);
                }
                try (ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT c.relrowsecurity FROM pg_class c"
                                + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                                + " WHERE n.nspname = '" + parts[0] + "' AND c.relname = '" + parts[1] + "'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean(1))
                            .as("row-level security enabled on %s", qualified)
                            .isTrue();
                }
                try (ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT count(*) FROM pg_policies WHERE schemaname = '" + parts[0]
                                + "' AND tablename = '" + parts[1] + "' AND policyname = 'tenant_isolation'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1))
                            .as("tenant_isolation policy on %s", qualified)
                            .isEqualTo(1);
                }
            }
        }
    }

    /** The history table lands in the migration schema, which app_user cannot reach. */
    @Test
    void historyTableLivesInTheMigrationSchema() throws SQLException {
        try (Connection conn = migrationUserConnection();
                ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT schemaname, tableowner FROM pg_tables"
                                + " WHERE tablename = 'flyway_schema_history'")) {
            assertThat(rs.next()).as("flyway_schema_history exists").isTrue();
            assertThat(rs.getString("schemaname")).isEqualTo("migration");
            assertThat(rs.getString("tableowner")).isEqualTo(PostgresTestContainerInitializer.MIGRATION_USER);
        }
    }

    // ── helpers

    /**
     * Every table in core, hrms, payroll and reference — that is, everything the shipped scripts
     * created. Read from the database rather than listed here, so a new migration is covered by
     * these assertions the day it is added and nobody has to remember to extend a list.
     */
    private static List<String> shippedTables() throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection conn = migrationUserConnection();
                ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT schemaname, tablename FROM pg_tables"
                                + " WHERE schemaname IN ('core', 'hrms', 'payroll', 'reference')"
                                + " ORDER BY schemaname, tablename")) {
            while (rs.next()) {
                tables.add(rs.getString(1) + "." + rs.getString(2));
            }
        }
        return tables;
    }

    private static Connection migrationUserConnection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }
}
