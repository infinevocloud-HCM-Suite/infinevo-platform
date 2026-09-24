package com.infinevo.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * W-11.1 §6 — the action catalogue, the three tenant-scoped role tables, and the seven system roles
 * seeded for every tenant.
 *
 * <p>Runs the shipped scripts through Flyway against a database of its own, then inserts tenants as
 * {@code migration_user} the way the dev seed does, so the {@code core.tenant} trigger in {@code
 * V022__role_action.sql} is what is under test — not a hand call to the seed function.
 */
@EnabledIfDockerAvailable
class RoleCatalogueIT {

    private static final String DATABASE = "infinevo_roles";

    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private static final List<String> SYSTEM_ROLES =
            List.of("platform-admin", "tenant-admin", "hr", "manager", "payroll-officer", "finance", "employee");

    private static final List<String> ROLE_TABLES = List.of("role", "role_action", "user_role");

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    private static String jdbcUrl;

    @BeforeAll
    static void applyShippedMigrationsAndCreateTenants() throws SQLException {
        jdbcUrl = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
        MigrationApplication.launch(
                "--DB_URL=" + jdbcUrl,
                "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
                "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);

        try (Connection conn = migrationUserConnection();
                PreparedStatement ps =
                        conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "Roles Alpha");
            ps.executeUpdate();
            ps.setObject(1, TENANT_B);
            ps.setString(2, "Roles Beta");
            ps.executeUpdate();
        }
    }

    // ── the catalogue

    @Test
    void catalogueIsSeeded_andCoversAllThreeModules() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            assertThat(count(conn, "SELECT count(*) FROM reference.action")).isPositive();
            assertThat(strings(conn, "SELECT DISTINCT module FROM reference.action ORDER BY 1"))
                    .containsExactly("core", "hrms", "payroll");
            assertThat(count(conn, "SELECT count(*) FROM reference.action WHERE split_part(code, '.', 1) <> module"))
                    .as("actions whose code prefix disagrees with their module")
                    .isZero();
        }
    }

    @Test
    void appUser_canReadTheCatalogue_butNotWriteIt() throws SQLException {
        try (Connection conn = appUserConnection()) {
            assertThat(count(conn, "SELECT count(*) FROM reference.action")).isPositive();
            assertThatThrownBy(() -> conn.createStatement()
                            .execute("INSERT INTO reference.action (code, name, module)"
                                    + " VALUES ('core.thing.invent', 'Invented', 'core')"))
                    .as("the catalogue is not writable at runtime (spec §4)")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    // ── the three tenant-scoped tables

    @Test
    void rowLevelSecurityAndTenantIsolationPolicy_onAllThreeTables() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            for (String table : ROLE_TABLES) {
                assertThat(count(
                                conn,
                                "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                                        + " WHERE n.nspname = 'core' AND c.relname = '" + table
                                        + "' AND c.relrowsecurity"))
                        .as("RLS enabled on core.%s", table)
                        .isEqualTo(1);
                assertThat(count(
                                conn,
                                "SELECT count(*) FROM pg_policies WHERE schemaname = 'core' AND tablename = '" + table
                                        + "' AND policyname = 'tenant_isolation'"))
                        .as("tenant_isolation policy on core.%s", table)
                        .isEqualTo(1);
            }
        }
    }

    // ── the seed

    @Test
    void insertingATenant_seedsSevenSystemRoles_eachHoldingAtLeastOneAction() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            Map<String, Integer> actionsPerRole = actionsPerSystemRole(conn, TENANT_A);
            assertThat(actionsPerRole.keySet()).containsExactlyInAnyOrderElementsOf(SYSTEM_ROLES);
            assertThat(actionsPerRole.values()).allSatisfy(n -> assertThat(n).isPositive());

            int catalogue = count(conn, "SELECT count(*) FROM reference.action");
            assertThat(actionsPerRole.get("platform-admin")).isEqualTo(catalogue);
            assertThat(actionsPerRole.get("tenant-admin"))
                    .as("everything but core.tenant.provision")
                    .isEqualTo(catalogue - 1);
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM core.role_action ra JOIN core.role r ON r.id = ra.role_id"
                                    + " WHERE ra.action_code = 'core.tenant.provision' AND r.tenant_id = '"
                                    + TENANT_A + "' AND r.code <> 'platform-admin'"))
                    .as("only platform-admin may provision tenants")
                    .isZero();
        }
    }

    @Test
    void seedIsIdempotent() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            Map<String, Integer> before = actionsPerSystemRole(conn, TENANT_B);
            conn.createStatement().execute("SELECT core.seed_system_roles('" + TENANT_B + "')");
            assertThat(actionsPerSystemRole(conn, TENANT_B)).isEqualTo(before);
        }
    }

    @Test
    void appUser_cannotCallTheSeedFunction() throws SQLException {
        try (Connection conn = appUserConnection()) {
            assertThatThrownBy(
                            () -> conn.createStatement().execute("SELECT core.seed_system_roles('" + TENANT_A + "')"))
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    // ── isolation

    @Test
    void appUserBoundToTenantA_seesOnlyTenantARoles() throws SQLException {
        try (Connection conn = appUserConnection()) {
            conn.setAutoCommit(false);
            bind(conn, TENANT_A);
            assertThat(count(conn, "SELECT count(*) FROM core.role")).isEqualTo(SYSTEM_ROLES.size());
            assertThat(count(conn, "SELECT count(*) FROM core.role WHERE tenant_id <> '" + TENANT_A + "'"))
                    .isZero();
            assertThat(count(conn, "SELECT count(*) FROM core.role_action WHERE tenant_id <> '" + TENANT_A + "'"))
                    .isZero();
            conn.rollback();
        }
    }

    @Test
    void roleActionCannotPointAtAnotherTenantsRole() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            UUID roleOfB = roleId(conn, TENANT_B, "hr");
            assertThatThrownBy(() -> conn.createStatement()
                            .execute("INSERT INTO core.role_action (tenant_id, role_id, action_code) VALUES ('"
                                    + TENANT_A + "', '" + roleOfB + "', 'core.org.read')"))
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(FOREIGN_KEY_VIOLATION);
        }
    }

    @Test
    void roleActionCannotHoldAnUnknownAction() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            UUID roleOfA = roleId(conn, TENANT_A, "hr");
            assertThatThrownBy(() -> conn.createStatement()
                            .execute("INSERT INTO core.role_action (tenant_id, role_id, action_code) VALUES ('"
                                    + TENANT_A + "', '" + roleOfA + "', 'core.nothing.invented')"))
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(FOREIGN_KEY_VIOLATION);
        }
    }

    @Test
    void userRoleCannotGrantAnotherTenantsRole_butCanGrantItsOwn() throws SQLException {
        try (Connection conn = migrationUserConnection()) {
            UUID userOfA = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.user_account"
                    + " (id, tenant_id, keycloak_user_id, email) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, userOfA);
                ps.setObject(2, TENANT_A);
                ps.setObject(3, UUID.randomUUID());
                ps.setString(4, "grant-probe@example.test");
                ps.executeUpdate();
            }
            UUID roleOfB = roleId(conn, TENANT_B, "employee");
            UUID roleOfA = roleId(conn, TENANT_A, "employee");

            assertThatThrownBy(() -> conn.createStatement()
                            .execute("INSERT INTO core.user_role (tenant_id, user_account_id, role_id) VALUES ('"
                                    + TENANT_A + "', '" + userOfA + "', '" + roleOfB + "')"))
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(FOREIGN_KEY_VIOLATION);

            assertThat(conn.createStatement()
                            .executeUpdate("INSERT INTO core.user_role (tenant_id, user_account_id, role_id) VALUES ('"
                                    + TENANT_A + "', '" + userOfA + "', '" + roleOfA + "')"))
                    .isEqualTo(1);
        }
    }

    // ── helpers

    private static Map<String, Integer> actionsPerSystemRole(Connection conn, UUID tenant) throws SQLException {
        Map<String, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT r.code, count(ra.id) FROM core.role r"
                + " LEFT JOIN core.role_action ra ON ra.role_id = r.id AND ra.tenant_id = r.tenant_id"
                + " WHERE r.tenant_id = ? AND r.is_system GROUP BY r.code ORDER BY r.code")) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return result;
    }

    private static UUID roleId(Connection conn, UUID tenant, String code) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT id FROM core.role WHERE tenant_id = ? AND code = ?")) {
            ps.setObject(1, tenant);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).as("role %s in tenant %s", code, tenant).isTrue();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static void bind(Connection conn, UUID tenant) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
            ps.setString(1, tenant.toString());
            ps.execute();
        }
    }

    private static int count(Connection conn, String sql) throws SQLException {
        try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }

    private static List<String> strings(Connection conn, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
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
}
