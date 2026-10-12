package com.infinevo.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.EnabledIfDockerAvailable;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D-68 — the payroll officer reads and saves the loss-of-pay policy, which needs {@code core.lop_policy.read}
 * and {@code core.lop_policy.manage}. V170 grants both through a trigger of its own for a tenant provisioned
 * afterwards and through {@code core.grant_lop_policy_actions} for the tenants that already exist; the platform
 * tenant (D-33, V158) is left out. Runs the shipped scripts, with tenants inserted as the schema owner.
 */
@EnabledIfDockerAvailable
class LopPolicyOfficerGrantIT {

    private static final String DATABASE = "infinevo_lop_policy_grant";
    private static final UUID PLATFORM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String READ = "core.lop_policy.read";
    private static final String MANAGE = "core.lop_policy.manage";

    private static final UUID NEW_TENANT = UUID.randomUUID();

    private static String jdbcUrl;

    @BeforeAll
    static void migrateAndProvision() throws SQLException {
        jdbcUrl = PostgresTestContainerInitializer.provisionAdditionalDatabase(DATABASE);
        MigrationApplication.launch(
                "--DB_URL=" + jdbcUrl,
                "--DB_MIGRATION_USERNAME=" + PostgresTestContainerInitializer.MIGRATION_USER,
                "--DB_MIGRATION_PASSWORD=" + PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
        try (Connection conn = connection()) {
            insertTenant(conn, NEW_TENANT, "LOP Policy New Tenant");
        }
    }

    @Test
    @DisplayName("A tenant provisioned after V170 gets both loss-of-pay policy actions on its payroll officer")
    void newOfficerHoldsBoth() throws SQLException {
        try (Connection conn = connection()) {
            assertThat(grants(conn, NEW_TENANT, "payroll-officer", READ)).isEqualTo(1);
            assertThat(grants(conn, NEW_TENANT, "payroll-officer", MANAGE)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Only the officer gains them: finance, hr, manager and employee are unchanged")
    void nobodyElseGainsThem() throws SQLException {
        try (Connection conn = connection()) {
            for (String role : new String[] {"finance", "hr", "manager", "employee"}) {
                assertThat(grants(conn, NEW_TENANT, role, READ)).as(role).isZero();
                assertThat(grants(conn, NEW_TENANT, role, MANAGE)).as(role).isZero();
            }
        }
    }

    @Test
    @DisplayName("The platform tenant's payroll officer holds neither (D-33)")
    void platformTenantIsLeftOut() throws SQLException {
        try (Connection conn = connection()) {
            assertThat(grants(conn, PLATFORM, "payroll-officer", READ)).isZero();
            assertThat(grants(conn, PLATFORM, "payroll-officer", MANAGE)).isZero();

            // The backfill function called for the platform tenant is a no-op, not a refused insert.
            backfill(conn, PLATFORM);
            assertThat(grants(conn, PLATFORM, "payroll-officer", READ)).isZero();
        }
    }

    @Test
    @DisplayName("The backfill function grants a tenant that lacks them, and running it twice changes nothing")
    void backfillGrantsAnExistingTenantOnce() throws SQLException {
        UUID existing = UUID.randomUUID();
        try (Connection conn = connection()) {
            insertTenant(conn, existing, "LOP Policy Existing Tenant");
            // What a tenant provisioned before V170 looks like: system roles, no loss-of-pay grants.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM core.role_action WHERE tenant_id = ? AND action_code IN (?, ?) AND role_id IN "
                            + "(SELECT id FROM core.role WHERE tenant_id = ? AND code = 'payroll-officer')")) {
                ps.setObject(1, existing);
                ps.setString(2, READ);
                ps.setString(3, MANAGE);
                ps.setObject(4, existing);
                assertThat(ps.executeUpdate()).isEqualTo(2);
            }

            backfill(conn, existing);
            backfill(conn, existing);

            assertThat(grants(conn, existing, "payroll-officer", READ)).isEqualTo(1);
            assertThat(grants(conn, existing, "payroll-officer", MANAGE)).isEqualTo(1);
        }
    }

    private static void insertTenant(Connection conn, UUID tenantId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private static void backfill(Connection conn, UUID tenantId) throws SQLException {
        try (CallableStatement cs = conn.prepareCall("SELECT core.grant_lop_policy_actions(?)")) {
            cs.setObject(1, tenantId);
            cs.execute();
        }
    }

    private static int grants(Connection conn, UUID tenantId, String roleCode, String actionCode) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.role_action ra "
                + "JOIN core.role r ON r.id = ra.role_id AND r.tenant_id = ra.tenant_id "
                + "WHERE ra.tenant_id = ? AND r.code = ? AND ra.action_code = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, roleCode);
            ps.setString(3, actionCode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }
}
