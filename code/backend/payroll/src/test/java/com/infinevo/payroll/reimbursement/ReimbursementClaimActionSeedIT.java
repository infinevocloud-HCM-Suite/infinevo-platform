package com.infinevo.payroll.reimbursement;

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
 * W-35.1 — Verifies reference.action seeds for reimbursement claims and role_action grants.
 */
@EnabledIfDockerAvailable
class ReimbursementClaimActionSeedIT extends AbstractIntegrationTest {

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

            // Provision a tenant before V097 runs to test backfill
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
                ps.setObject(1, PRE_EXISTING_TENANT);
                ps.setString(2, "Reimb Pre-Existing Tenant");
                ps.executeUpdate();
            }
            // Seed roles for pre-existing tenant
            try (CallableStatement cs = conn.prepareCall("SELECT core.seed_system_roles(?)")) {
                cs.setObject(1, PRE_EXISTING_TENANT);
                cs.execute();
            }

            PayrollTestSchema.executeResource(conn, "db/migration/reference/V097__reimbursement_claim_actions.sql");

            // Provision a tenant after V097 runs to test function redefinition
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
                ps.setObject(1, NEW_TENANT);
                ps.setString(2, "Reimb New Tenant");
                ps.executeUpdate();
            }
            try (CallableStatement cs = conn.prepareCall("SELECT core.seed_system_roles(?)")) {
                cs.setObject(1, NEW_TENANT);
                cs.execute();
            }
        }
    }

    @Test
    @DisplayName("The three reimbursement claim action codes exist in reference.action")
    void threeCodesExistInReferenceAction() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT code, name, module FROM reference.action WHERE code LIKE 'payroll.reimbursement_claim.%' ORDER BY code");
                ResultSet rs = ps.executeQuery()) {

            List<String> codes = new ArrayList<>();
            while (rs.next()) {
                codes.add(rs.getString("code"));
                assertThat(rs.getString("module")).isEqualTo("payroll");
            }

            assertThat(codes)
                    .containsExactly(
                            "payroll.reimbursement_claim.read",
                            "payroll.reimbursement_claim.read_own",
                            "payroll.reimbursement_claim.submit_own");
        }
    }

    @Test
    @DisplayName("payroll-officer and finance hold payroll.reimbursement_claim.read in pre-existing tenant")
    void officersHoldReadInPreExistingTenant() throws SQLException {
        List<String> officerActions = actionsForRole(PRE_EXISTING_TENANT, "payroll-officer");
        assertThat(officerActions).contains("payroll.reimbursement_claim.read");
        assertThat(officerActions)
                .doesNotContain("payroll.reimbursement_claim.read_own", "payroll.reimbursement_claim.submit_own");

        List<String> financeActions = actionsForRole(PRE_EXISTING_TENANT, "finance");
        assertThat(financeActions).contains("payroll.reimbursement_claim.read");
        assertThat(financeActions)
                .doesNotContain("payroll.reimbursement_claim.read_own", "payroll.reimbursement_claim.submit_own");
    }

    @Test
    @DisplayName("employee holds submit_own and read_own in pre-existing tenant")
    void employeeHoldsOwnCodesInPreExistingTenant() throws SQLException {
        List<String> actions = actionsForRole(PRE_EXISTING_TENANT, "employee");
        assertThat(actions).contains("payroll.reimbursement_claim.read_own", "payroll.reimbursement_claim.submit_own");
        assertThat(actions).doesNotContain("payroll.reimbursement_claim.read");
    }

    @Test
    @DisplayName("A tenant provisioned after the migration holds the same grants")
    void newTenantHoldsSameGrants() throws SQLException {
        List<String> officerActions = actionsForRole(NEW_TENANT, "payroll-officer");
        assertThat(officerActions).contains("payroll.reimbursement_claim.read");

        List<String> financeActions = actionsForRole(NEW_TENANT, "finance");
        assertThat(financeActions).contains("payroll.reimbursement_claim.read");

        List<String> employeeActions = actionsForRole(NEW_TENANT, "employee");
        assertThat(employeeActions)
                .contains("payroll.reimbursement_claim.read_own", "payroll.reimbursement_claim.submit_own");
        assertThat(employeeActions).doesNotContain("payroll.reimbursement_claim.read");

        List<String> adminActions = actionsForRole(NEW_TENANT, "tenant-admin");
        assertThat(adminActions)
                .contains(
                        "payroll.reimbursement_claim.read",
                        "payroll.reimbursement_claim.read_own",
                        "payroll.reimbursement_claim.submit_own");
    }

    private static List<String> actionsForRole(UUID tenantId, String roleCode) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT ra.action_code FROM core.role_action ra "
                        + "JOIN core.role r ON r.id = ra.role_id AND r.tenant_id = ra.tenant_id "
                        + "WHERE ra.tenant_id = ? AND r.code = ? AND ra.action_code LIKE 'payroll.reimbursement_claim.%' "
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
