package com.infinevo.payroll.deduction;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.CallableStatement;
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
 * W-35.2 §7 — {@code V100}: the three codes exist; {@code payroll-officer} holds {@code read} and
 * {@code manage}, {@code finance} holds {@code read}, {@code employee} holds {@code read_own}, for a
 * tenant that existed before the script and one provisioned after it. And the redefined seed function
 * still carries {@code V097}'s grants — the copy lost nothing.
 */
@EnabledIfDockerAvailable
class EmployeeDeductionActionSeedIT extends AbstractIntegrationTest {

    private static final UUID PRE_EXISTING_TENANT = UUID.randomUUID();
    private static final UUID NEW_TENANT = UUID.randomUUID();

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
            if (!PayrollTestSchema.actionExists(conn, "core.document.read_own")) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V025__catalogue_correction.sql");
            }
            if (!PayrollTestSchema.actionExists(conn, "payroll.fbp.read")) {
                PayrollTestSchema.executeResource(conn, "db/migration/reference/V052__fbp_actions.sql");
            }
            // The state before V100: V097's seed function in place.
            PayrollTestSchema.executeResource(conn, "db/migration/reference/V097__reimbursement_claim_actions.sql");

            provision(conn, PRE_EXISTING_TENANT, "Deduction Pre-Existing Tenant");
            PayrollTestSchema.executeResource(conn, "db/migration/reference/V100__employee_deduction_actions.sql");
            provision(conn, NEW_TENANT, "Deduction New Tenant");
        }
    }

    @Test
    @DisplayName("The three deduction action codes exist in reference.action, module payroll")
    void threeCodesExist() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT code, module FROM reference.action "
                        + "WHERE code LIKE 'payroll.employee_deduction.%' ORDER BY code");
                ResultSet rs = ps.executeQuery()) {
            List<String> codes = new ArrayList<>();
            while (rs.next()) {
                codes.add(rs.getString("code"));
                assertThat(rs.getString("module")).isEqualTo("payroll");
            }
            assertThat(codes)
                    .containsExactly(
                            "payroll.employee_deduction.manage",
                            "payroll.employee_deduction.read",
                            "payroll.employee_deduction.read_own");
        }
    }

    @Test
    @DisplayName("A tenant that existed before V100 holds the grants by role")
    void preExistingTenantGrants() throws SQLException {
        assertGrants(PRE_EXISTING_TENANT);
    }

    @Test
    @DisplayName("A tenant provisioned after V100 holds the same grants")
    void newTenantGrants() throws SQLException {
        assertGrants(NEW_TENANT);
    }

    @Test
    @DisplayName("The redefined seed function lost nothing: a new tenant still gets V097's reimbursement grants")
    void earlierGrantsKept() throws SQLException {
        assertThat(actions(NEW_TENANT, "payroll-officer", "payroll.reimbursement_claim.%"))
                .contains("payroll.reimbursement_claim.read");
        assertThat(actions(NEW_TENANT, "employee", "payroll.reimbursement_claim.%"))
                .contains("payroll.reimbursement_claim.read_own", "payroll.reimbursement_claim.submit_own");
        assertThat(actions(NEW_TENANT, "payroll-officer", "payroll.fbp.%")).contains("payroll.fbp.read");
        assertThat(actions(NEW_TENANT, "payroll-officer", "payroll.run.%"))
                .contains("payroll.run.read", "payroll.run.execute");
    }

    private static void assertGrants(UUID tenant) throws SQLException {
        String codes = "payroll.employee_deduction.%";
        assertThat(actions(tenant, "payroll-officer", codes))
                .containsExactly("payroll.employee_deduction.manage", "payroll.employee_deduction.read");
        assertThat(actions(tenant, "finance", codes)).containsExactly("payroll.employee_deduction.read");
        assertThat(actions(tenant, "employee", codes)).containsExactly("payroll.employee_deduction.read_own");
        assertThat(actions(tenant, "manager", codes)).isEmpty();
        assertThat(actions(tenant, "hr", codes)).isEmpty();
        assertThat(actions(tenant, "tenant-admin", codes))
                .containsExactly(
                        "payroll.employee_deduction.manage",
                        "payroll.employee_deduction.read",
                        "payroll.employee_deduction.read_own");
    }

    private static void provision(Connection conn, UUID tenant, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenant);
            ps.setString(2, name);
            ps.executeUpdate();
        }
        try (CallableStatement cs = conn.prepareCall("SELECT core.seed_system_roles(?)")) {
            cs.setObject(1, tenant);
            cs.execute();
        }
    }

    private static List<String> actions(UUID tenantId, String roleCode, String like) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT ra.action_code FROM core.role_action ra "
                        + "JOIN core.role r ON r.id = ra.role_id AND r.tenant_id = ra.tenant_id "
                        + "WHERE ra.tenant_id = ? AND r.code = ? AND ra.action_code LIKE ? ORDER BY ra.action_code")) {
            ps.setObject(1, tenantId);
            ps.setString(2, roleCode);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> actions = new ArrayList<>();
                while (rs.next()) {
                    actions.add(rs.getString(1));
                }
                return actions;
            }
        }
    }
}
