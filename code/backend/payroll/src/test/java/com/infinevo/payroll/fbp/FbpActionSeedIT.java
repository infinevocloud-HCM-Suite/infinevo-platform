package com.infinevo.payroll.fbp;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-27.2 — Verifies reference.action seeds for Flexible Benefit Plan and role_action grants.
 *
 * <p>Validates:
 * <ul>
 *   <li>The three codes exist in {@code reference.action}</li>
 *   <li>{@code payroll-officer} holds {@code payroll.fbp.read}</li>
 *   <li>{@code employee} holds {@code payroll.fbp.read_own} and {@code payroll.fbp.declare_own}</li>
 *   <li>The grants apply to pre-existing tenants provisioned before V052 ran</li>
 * </ul>
 */
@EnabledIfDockerAvailable
class FbpActionSeedIT extends AbstractIntegrationTest {

    private static final UUID PRE_EXISTING_TENANT = UUID.randomUUID();

    @BeforeAll
    static void setUpSchemaAndSeed() throws Exception {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            if (!PayrollTestSchema.tableExists(conn, "core", "tenant")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!PayrollTestSchema.tableExists(conn, "reference", "action")) {
                PayrollTestSchema.executeResource(conn, "db/migration/reference/V020__action.sql");
            }
            if (!PayrollTestSchema.tableExists(conn, "core", "role")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V021__role.sql");
            }
            if (!PayrollTestSchema.tableExists(conn, "core", "role_action")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V022__role_action.sql");
            }

            // Provision a tenant before V052 runs to prove backfill behavior
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
                ps.setObject(1, PRE_EXISTING_TENANT);
                ps.setString(2, "FBP Pre-Existing Tenant");
                ps.executeUpdate();
            }

            PayrollTestSchema.executeResource(conn, "db/migration/reference/V052__fbp_actions.sql");
        }
    }

    @Test
    @DisplayName("The three FBP action codes exist in reference.action")
    void threeCodesExistInReferenceAction() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT code, name, module FROM reference.action WHERE code LIKE 'payroll.fbp.%' ORDER BY code");
                ResultSet rs = ps.executeQuery()) {

            List<String> codes = new ArrayList<>();
            while (rs.next()) {
                codes.add(rs.getString("code"));
                assertThat(rs.getString("module")).isEqualTo("payroll");
            }

            assertThat(codes).containsExactly("payroll.fbp.declare_own", "payroll.fbp.read", "payroll.fbp.read_own");
        }
    }

    @Test
    @DisplayName("payroll-officer holds payroll.fbp.read in pre-existing tenant")
    void payrollOfficerHoldsRead() throws SQLException {
        List<String> actions = actionsForRole(PRE_EXISTING_TENANT, "payroll-officer");
        assertThat(actions).contains("payroll.fbp.read");
        assertThat(actions).doesNotContain("payroll.fbp.read_own", "payroll.fbp.declare_own");
    }

    @Test
    @DisplayName("employee holds payroll.fbp.read_own and payroll.fbp.declare_own in pre-existing tenant")
    void employeeHoldsOwnCodes() throws SQLException {
        List<String> actions = actionsForRole(PRE_EXISTING_TENANT, "employee");
        assertThat(actions).contains("payroll.fbp.read_own", "payroll.fbp.declare_own");
        assertThat(actions).doesNotContain("payroll.fbp.read");
    }

    @Test
    @DisplayName("platform-admin and tenant-admin hold all three FBP codes")
    void adminsHoldAllFbpCodes() throws SQLException {
        List<String> platformAdminActions = actionsForRole(PRE_EXISTING_TENANT, "platform-admin");
        assertThat(platformAdminActions)
                .contains("payroll.fbp.read", "payroll.fbp.read_own", "payroll.fbp.declare_own");

        List<String> tenantAdminActions = actionsForRole(PRE_EXISTING_TENANT, "tenant-admin");
        assertThat(tenantAdminActions).contains("payroll.fbp.read", "payroll.fbp.read_own", "payroll.fbp.declare_own");
    }

    private static List<String> actionsForRole(UUID tenantId, String roleCode) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT ra.action_code FROM core.role_action ra "
                        + "JOIN core.role r ON r.id = ra.role_id AND r.tenant_id = ra.tenant_id "
                        + "WHERE ra.tenant_id = ? AND r.code = ? AND ra.action_code LIKE 'payroll.fbp.%' "
                        + "ORDER BY ra.action_code")) {
            ps.setObject(1, tenantId);
            ps.setString(2, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> actions = new ArrayList<>();
                while (rs.next()) {
                    actions.add(rs.getString("action_code"));
                }
                return actions;
            }
        }
    }
}
