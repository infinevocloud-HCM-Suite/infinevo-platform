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
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * W-65.2 review: the two platform-only actions, {@code core.tenant.provision} and
 * {@code core.tenant.impersonate}, can be held only by {@code platform-admin} in the Infinevo platform tenant
 * ({@code V083}, {@code V136}). Run against the shipped scripts, with tenants inserted as the schema owner, so the
 * seed trigger and the guard triggers are what is under test.
 */
@EnabledIfDockerAvailable
class PlatformOnlyActionGuardIT {

    private static final String DATABASE = "infinevo_platform_guard";
    private static final UUID PLATFORM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CHECK_VIOLATION = "23514";

    private static final UUID CUSTOMER = UUID.randomUUID();

    private static String jdbcUrl;

    @BeforeAll
    static void migrateAndCreateACustomer() throws SQLException {
        jdbcUrl = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
        MigrationApplication.launch(
                "--DB_URL=" + jdbcUrl,
                "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
                "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
        try (Connection conn = connection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.tenant (tenant_id, name) VALUES (?, 'Guard Customer')")) {
            ps.setObject(1, CUSTOMER);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Only platform-admin in the platform tenant holds provision and impersonate")
    void onlyPlatformAdminInThePlatformTenantHoldsThem() throws SQLException {
        try (Connection conn = connection()) {
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM core.role_action ra JOIN core.role r ON r.id = ra.role_id"
                                    + " WHERE ra.action_code IN ('core.tenant.provision', 'core.tenant.impersonate')"
                                    + " AND NOT (ra.tenant_id = '" + PLATFORM
                                    + "' AND r.code = 'platform-admin' AND r.is_system)"))
                    .as("grants of either action anywhere else")
                    .isZero();
            assertThat(count(
                            conn,
                            "SELECT count(*) FROM core.role_action ra JOIN core.role r ON r.id = ra.role_id"
                                    + " WHERE ra.tenant_id = '" + PLATFORM + "' AND r.code = 'platform-admin'"
                                    + " AND ra.action_code IN ('core.tenant.provision', 'core.tenant.impersonate')"))
                    .as("the platform's own platform-admin keeps both")
                    .isEqualTo(2);
        }
    }

    @Test
    @DisplayName("A customer role cannot be given core.tenant.provision: the insert is refused")
    void provisionOnACustomerRoleIsRefused() throws SQLException {
        try (Connection conn = connection()) {
            UUID role = customRole(conn, "custom-provisioner");

            assertThatThrownBy(() -> grant(conn, CUSTOMER, role, "core.tenant.provision"))
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo(CHECK_VIOLATION);
            assertThat(count(conn, "SELECT count(*) FROM core.role_action WHERE role_id = '" + role + "'"))
                    .isZero();
        }
    }

    @Test
    @DisplayName("core.tenant.provision is refused on the seeded customer platform-admin and tenant-admin roles too")
    void provisionOnASeededCustomerRoleIsRefused() throws SQLException {
        try (Connection conn = connection()) {
            for (String code : new String[] {"platform-admin", "tenant-admin"}) {
                UUID role = roleId(conn, CUSTOMER, code);
                assertThatThrownBy(() -> grant(conn, CUSTOMER, role, "core.tenant.provision"))
                        .as(code)
                        .isInstanceOf(PSQLException.class);
            }
        }
    }

    @Test
    @DisplayName("core.tenant.impersonate on a customer role is dropped quietly, so seeding still succeeds")
    void impersonateOnACustomerRoleIsDropped() throws SQLException {
        try (Connection conn = connection()) {
            UUID role = customRole(conn, "custom-impersonator");

            grant(conn, CUSTOMER, role, "core.tenant.impersonate");

            assertThat(count(conn, "SELECT count(*) FROM core.role_action WHERE role_id = '" + role + "'"))
                    .as("the row never lands")
                    .isZero();
        }
    }

    @Test
    @DisplayName("A custom role in the platform tenant cannot hold provision either: only platform-admin does")
    void aCustomPlatformRoleCannotHoldProvision() throws SQLException {
        try (Connection conn = connection()) {
            UUID role = customRole(conn, PLATFORM, "custom-platform-" + UUID.randomUUID());

            assertThatThrownBy(() -> grant(conn, PLATFORM, role, "core.tenant.provision"))
                    .isInstanceOf(PSQLException.class);
        }
    }

    private static UUID customRole(Connection conn, String code) throws SQLException {
        return customRole(conn, CUSTOMER, code + "-" + UUID.randomUUID());
    }

    private static UUID customRole(Connection conn, UUID tenant, String code) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("INSERT INTO core.role (tenant_id, code, name) VALUES (?, ?, ?) RETURNING id")) {
            ps.setObject(1, tenant);
            ps.setString(2, code);
            ps.setString(3, code);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static UUID roleId(Connection conn, UUID tenant, String code) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT id FROM core.role WHERE tenant_id = ? AND code = ?")) {
            ps.setObject(1, tenant);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static void grant(Connection conn, UUID tenant, UUID role, String action) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.role_action (tenant_id, role_id, action_code) VALUES (?, ?, ?)")) {
            ps.setObject(1, tenant);
            ps.setObject(2, role);
            ps.setString(3, action);
            ps.executeUpdate();
        }
    }

    private static long count(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }
}
