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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D-75 — "My self-service" shows for anyone with a linked employee record, so every seeded role that may be on the
 * payroll holds {@code core.employee.read_own}. V171 grants it to hr, manager, payroll-officer and finance through a
 * trigger of its own for a tenant provisioned afterwards and through {@code core.grant_employee_read_own_actions}
 * for the tenants that already exist; the platform tenant (D-33, V158) is left out. Runs the shipped scripts, with
 * tenants inserted as the schema owner.
 */
@EnabledIfDockerAvailable
class EmployeeReadOwnGrantIT {

    private static final String DATABASE = "infinevo_employee_read_own_grant";
    private static final UUID PLATFORM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String READ_OWN = "core.employee.read_own";
    private static final List<String> GRANTED = List.of("hr", "manager", "payroll-officer", "finance");

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
            insertTenant(conn, NEW_TENANT, "Read Own New Tenant");
        }
    }

    @Test
    @DisplayName("A tenant provisioned after V171: hr, manager, payroll-officer and finance each hold read_own once")
    void newTenantRolesHoldReadOwn() throws SQLException {
        try (Connection conn = connection()) {
            for (String role : GRANTED) {
                assertThat(grants(conn, NEW_TENANT, role, READ_OWN)).as(role).isEqualTo(1);
            }
            // The roles that held it before V171 still do, exactly once.
            for (String role : List.of("employee", "tenant-admin", "platform-admin")) {
                assertThat(grants(conn, NEW_TENANT, role, READ_OWN)).as(role).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("The platform tenant's roles hold it on no role (D-33)")
    void platformTenantIsLeftOut() throws SQLException {
        try (Connection conn = connection()) {
            for (String role : GRANTED) {
                assertThat(grants(conn, PLATFORM, role, READ_OWN)).as(role).isZero();
            }

            // The backfill function called for the platform tenant is a no-op, not a refused insert.
            backfill(conn, PLATFORM);
            for (String role : GRANTED) {
                assertThat(grants(conn, PLATFORM, role, READ_OWN)).as(role).isZero();
            }
        }
    }

    @Test
    @DisplayName("The backfill function grants a tenant that lacks it, and running it twice changes nothing")
    void backfillGrantsAnExistingTenantOnce() throws SQLException {
        UUID existing = UUID.randomUUID();
        try (Connection conn = connection()) {
            insertTenant(conn, existing, "Read Own Existing Tenant");
            // What a tenant provisioned before V171 looks like: system roles, read_own on the employee and admins only.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM core.role_action WHERE tenant_id = ? AND action_code = ? AND role_id IN "
                            + "(SELECT id FROM core.role WHERE tenant_id = ? "
                            + "AND code IN ('hr', 'manager', 'payroll-officer', 'finance'))")) {
                ps.setObject(1, existing);
                ps.setString(2, READ_OWN);
                ps.setObject(3, existing);
                assertThat(ps.executeUpdate()).isEqualTo(4);
            }

            backfill(conn, existing);
            int afterFirst = total(conn, existing);
            backfill(conn, existing);

            for (String role : GRANTED) {
                assertThat(grants(conn, existing, role, READ_OWN)).as(role).isEqualTo(1);
            }
            assertThat(total(conn, existing)).isEqualTo(afterFirst);
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
        try (CallableStatement cs = conn.prepareCall("SELECT core.grant_employee_read_own_actions(?)")) {
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

    private static int total(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT count(*) FROM core.role_action WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
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
