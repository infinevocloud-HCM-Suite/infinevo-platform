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
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * W-114.1 ({@code V192}): the AI helper switch and monthly cap on {@code core.subscription}, the setter
 * {@code core.set_subscription_ai}, the {@code core.ai_usage} table and the three new columns on
 * {@code core.list_tenants()} and {@code core.get_tenant_overview(uuid)}. Runs the shipped scripts through Flyway.
 */
@EnabledIfDockerAvailable
class AiSwitchAndUsageIT {

    private static final String DATABASE = "infinevo_ai_switch";
    private static final String CHECK_VIOLATION = "23514";
    private static final String SETTER = "core.set_subscription_ai(uuid,boolean,integer)";
    private static final String CURRENT_MONTH = "date_trunc('month', now() AT TIME ZONE 'UTC')::date";

    private static final List<String> OVERVIEW_COLUMNS = List.of(
            "tenant_id",
            "name",
            "country_code",
            "timezone",
            "status",
            "modules",
            "created_at",
            "current_period_end",
            "user_count",
            "admin_invitation_email",
            "admin_invitation_status",
            "employee_count",
            "employee_limit",
            "plan",
            "support_plan",
            "ai_enabled",
            "ai_monthly_request_cap",
            "ai_usage_this_month");

    private static String jdbcUrl;

    @BeforeAll
    static void migrate() {
        jdbcUrl = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
        MigrationApplication.launch(
                "--DB_URL=" + jdbcUrl,
                "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
                "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    // ── core.subscription

    @Test
    @DisplayName("A new subscription is off with no cap; no existing subscription is switched on")
    void defaultsAreOffAndNoCap() throws SQLException {
        try (Connection conn = migrationUser()) {
            UUID tenant = tenantWithSubscription(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ai_enabled, ai_monthly_request_cap FROM core.subscription WHERE tenant_id = ?")) {
                ps.setObject(1, tenant);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean(1)).isFalse();
                    assertThat(rs.getObject(2)).isNull();
                }
            }
            assertThat(count(conn, "SELECT count(*) FROM core.subscription WHERE ai_enabled"))
                    .isZero();
        }
    }

    @Test
    @DisplayName("Switched on with no cap is refused, and a cap of 0 is refused")
    void checkConstraintsRefuseBadSettings() throws SQLException {
        try (Connection conn = migrationUser()) {
            UUID tenant = tenantWithSubscription(conn);
            assertThatThrownBy(() -> update(conn, tenant, true, null))
                    .as("ai_enabled with a NULL cap")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(CHECK_VIOLATION);
            assertThatThrownBy(() -> update(conn, tenant, false, 0))
                    .as("cap 0")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(CHECK_VIOLATION);
            assertThatThrownBy(() -> setAi(conn, tenant, true, null))
                    .as("core.set_subscription_ai on with no cap")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(CHECK_VIOLATION);
        }
    }

    // ── core.set_subscription_ai

    @Test
    @DisplayName("core.set_subscription_ai writes both columns, and switching off keeps the cap given")
    void setterWritesBothColumns() throws SQLException {
        try (Connection conn = migrationUser()) {
            UUID tenant = tenantWithSubscription(conn);
            setAi(conn, tenant, true, 500);
            assertSettings(conn, tenant, true, 500);
            setAi(conn, tenant, false, 500);
            assertSettings(conn, tenant, false, 500);
        }
    }

    @Test
    @DisplayName("core.set_subscription_ai raises for a tenant with no subscription")
    void setterRaisesForUnknownTenant() {
        assertThatThrownBy(() -> {
                    try (Connection conn = migrationUser()) {
                        setAi(conn, UUID.randomUUID(), true, 10);
                    }
                })
                .isInstanceOf(PSQLException.class)
                .hasMessageContaining("Subscription not found for tenant");
    }

    @Test
    @DisplayName("core.set_subscription_ai is SECURITY DEFINER, not executable by PUBLIC, executable by app_user")
    void setterGrants() throws SQLException {
        try (Connection conn = migrationUser()) {
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM pg_proc WHERE oid = '" + SETTER + "'::regprocedure"
                                    + " AND prosecdef"))
                    .as("SECURITY DEFINER")
                    .isEqualTo(1);
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM pg_proc p, aclexplode(p.proacl) a"
                                    + " WHERE p.oid = '" + SETTER + "'::regprocedure"
                                    + " AND a.grantee = 0 AND a.privilege_type = 'EXECUTE'"))
                    .as("EXECUTE granted to PUBLIC")
                    .isZero();
            // proacl NULL would mean the default, which grants EXECUTE to PUBLIC.
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM pg_proc WHERE oid = '" + SETTER + "'::regprocedure"
                                    + " AND proacl IS NOT NULL"))
                    .as("explicit ACL")
                    .isEqualTo(1);
            assertThat(bool(conn, "SELECT has_function_privilege('app_user', '" + SETTER + "', 'EXECUTE')"))
                    .isTrue();
            assertThat(bool(conn, "SELECT has_function_privilege('readonly_user', '" + SETTER + "', 'EXECUTE')"))
                    .isFalse();
        }
    }

    // ── core.ai_usage

    @Test
    @DisplayName("core.ai_usage has RLS enabled, one tenant_isolation policy and the unique (tenant_id, usage_month)")
    void usageTableIsTenantScoped() throws SQLException {
        try (Connection conn = migrationUser()) {
            assertThat(bool(
                            conn,
                            "SELECT c.relrowsecurity FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                                    + " WHERE n.nspname = 'core' AND c.relname = 'ai_usage'"))
                    .isTrue();
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM pg_policies WHERE schemaname = 'core'"
                                    + " AND tablename = 'ai_usage' AND policyname = 'tenant_isolation'"))
                    .isEqualTo(1);
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM pg_indexes WHERE schemaname = 'core'"
                                    + " AND tablename = 'ai_usage' AND indexname = 'uk_ai_usage_tenant_month'"
                                    + " AND indexdef LIKE 'CREATE UNIQUE INDEX%(tenant_id, usage_month)'"))
                    .isEqualTo(1);

            UUID tenant = tenantWithSubscription(conn);
            insertUsage(conn, tenant, 1);
            assertThatThrownBy(() -> insertUsage(conn, tenant, 2))
                    .as("a second row for the same tenant and month")
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo("23505");
            assertThat(bool(conn, "SELECT has_table_privilege('app_user', 'core.ai_usage', 'SELECT, INSERT, UPDATE')"))
                    .isTrue();
            assertThat(bool(conn, "SELECT has_table_privilege('app_user', 'core.ai_usage', 'DELETE')"))
                    .isFalse();
        }
    }

    // ── core.list_tenants() and core.get_tenant_overview(uuid)

    @Test
    @DisplayName(
            "Both functions return the V188 columns in order, then ai_enabled, ai_monthly_request_cap, ai_usage_this_month")
    void functionsEndWithTheThreeNewColumns() throws SQLException {
        try (Connection conn = migrationUser()) {
            assertThat(columns(conn, "SELECT * FROM core.list_tenants() LIMIT 0"))
                    .isEqualTo(OVERVIEW_COLUMNS);
            assertThat(columns(conn, "SELECT * FROM core.get_tenant_overview(gen_random_uuid())"))
                    .isEqualTo(OVERVIEW_COLUMNS);
        }
    }

    @Test
    @DisplayName(
            "As app_user bound to another tenant, the overview still reports this month's usage; the table itself does not")
    void overviewReadsUsageAcrossTenants() throws SQLException {
        UUID tenantA;
        UUID tenantB;
        try (Connection conn = migrationUser()) {
            tenantA = tenantWithSubscription(conn);
            tenantB = tenantWithSubscription(conn);
            setAi(conn, tenantA, true, 500);
            insertUsage(conn, tenantA, 12);
            // Last month's row is history, not this month's usage.
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.ai_usage (tenant_id, usage_month,"
                    + " request_count) VALUES (?, (" + CURRENT_MONTH + " - interval '1 month')::date, 99)")) {
                ps.setObject(1, tenantA);
                ps.executeUpdate();
            }
        }

        try (Connection conn = appUser()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                ps.setString(1, tenantB.toString());
                ps.execute();
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT ai_enabled, ai_monthly_request_cap,"
                    + " ai_usage_this_month FROM core.get_tenant_overview(?)")) {
                ps.setObject(1, tenantA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean(1)).isTrue();
                    assertThat(rs.getInt(2)).isEqualTo(500);
                    assertThat(rs.getInt(3)).isEqualTo(12);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT ai_enabled, ai_monthly_request_cap,"
                    + " ai_usage_this_month FROM core.list_tenants() WHERE tenant_id = ?")) {
                ps.setObject(1, tenantB);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean(1)).isFalse();
                    assertThat(rs.getObject(2)).isNull();
                    assertThat(rs.getInt(3)).as("no usage row is 0").isZero();
                }
            }
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.ai_usage WHERE tenant_id = ?")) {
                ps.setObject(1, tenantA);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1))
                            .as("tenant A's rows seen by tenant B")
                            .isZero();
                }
            }
            conn.rollback();
        }
    }

    // ── helpers

    private static UUID tenantWithSubscription(Connection conn) throws SQLException {
        UUID tenant = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, tenant);
            ps.setString(2, "AI Switch " + tenant);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.subscription (tenant_id, status)"
                + " VALUES (?, 'ACTIVE') ON CONFLICT (tenant_id) DO NOTHING")) {
            ps.setObject(1, tenant);
            ps.executeUpdate();
        }
        return tenant;
    }

    private static void update(Connection conn, UUID tenant, boolean enabled, Integer cap) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE core.subscription SET ai_enabled = ?, ai_monthly_request_cap = ? WHERE tenant_id = ?")) {
            ps.setBoolean(1, enabled);
            setCap(ps, 2, cap);
            ps.setObject(3, tenant);
            ps.executeUpdate();
        }
    }

    private static void setAi(Connection conn, UUID tenant, boolean enabled, Integer cap) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT core.set_subscription_ai(?, ?, ?)")) {
            ps.setObject(1, tenant);
            ps.setBoolean(2, enabled);
            setCap(ps, 3, cap);
            ps.execute();
        }
    }

    private static void setCap(PreparedStatement ps, int index, Integer cap) throws SQLException {
        if (cap == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, cap);
        }
    }

    private static void assertSettings(Connection conn, UUID tenant, boolean enabled, int cap) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ai_enabled, ai_monthly_request_cap FROM core.subscription WHERE tenant_id = ?")) {
            ps.setObject(1, tenant);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isEqualTo(enabled);
                assertThat(rs.getInt(2)).isEqualTo(cap);
            }
        }
    }

    private static void insertUsage(Connection conn, UUID tenant, int requests) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.ai_usage (tenant_id, usage_month,"
                + " request_count) VALUES (?, " + CURRENT_MONTH + ", ?)")) {
            ps.setObject(1, tenant);
            ps.setInt(2, requests);
            ps.executeUpdate();
        }
    }

    private static List<String> columns(Connection conn, String sql) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                names.add(rs.getMetaData().getColumnName(i));
            }
        }
        return names;
    }

    private static long count(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static boolean bool(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getBoolean(1);
        }
    }

    private static Connection migrationUser() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    private static Connection appUser() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl, PostgresTestContainerInitializer.APP_USER, PostgresTestContainerInitializer.APP_USER_PASSWORD);
    }
}
