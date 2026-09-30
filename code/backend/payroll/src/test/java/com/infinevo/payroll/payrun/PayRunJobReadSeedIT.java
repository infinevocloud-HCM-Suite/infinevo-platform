package com.infinevo.payroll.payrun;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-29.4 §4 — the payroll officer follows a pay run's job on {@code GET /api/v1/jobs/{jobId}}, which
 * needs {@code core.job.read}. V126 grants it to the officers of tenants that already exist and to
 * those provisioned afterwards, and takes nothing away from the grants V097 left.
 */
@EnabledIfDockerAvailable
class PayRunJobReadSeedIT extends AbstractIntegrationTest {

    private static final String JOB_READ = "core.job.read";
    private static final UUID PRE_EXISTING_TENANT = UUID.randomUUID();
    private static final UUID NEW_TENANT = UUID.randomUUID();

    private static boolean officerHeldItBefore;

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
            if (!PayrollTestSchema.actionExists(conn, JOB_READ)) {
                PayrollTestSchema.executeResource(conn, "db/migration/core/V025__catalogue_correction.sql");
            }
            if (!PayrollTestSchema.actionExists(conn, "payroll.fbp.read")) {
                PayrollTestSchema.executeResource(conn, "db/migration/reference/V052__fbp_actions.sql");
            }
            // V097 is the seed function V126 builds on; applying it again is harmless, and it puts
            // the function back to what a tenant provisioned before V126 was seeded with.
            PayrollTestSchema.executeResource(conn, "db/migration/reference/V097__reimbursement_claim_actions.sql");

            provision(conn, PRE_EXISTING_TENANT, "Job Read Pre-Existing Tenant");
            officerHeldItBefore = holds(conn, PRE_EXISTING_TENANT, "payroll-officer", JOB_READ);

            PayrollTestSchema.executeResource(conn, "db/migration/reference/V126__payroll_officer_job_read.sql");

            provision(conn, NEW_TENANT, "Job Read New Tenant");
        }
    }

    @Test
    @DisplayName("Before V126 the payroll officer did not hold core.job.read")
    void officerLackedItBefore() {
        assertThat(officerHeldItBefore).isFalse();
    }

    @Test
    @DisplayName("V126 grants core.job.read to the payroll officer of a tenant that already existed")
    void preExistingOfficerHoldsIt() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            assertThat(holds(conn, PRE_EXISTING_TENANT, "payroll-officer", JOB_READ))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("A tenant provisioned after V126 gets the grant from the seed function")
    void newOfficerHoldsIt() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            assertThat(holds(conn, NEW_TENANT, "payroll-officer", JOB_READ)).isTrue();
        }
    }

    @Test
    @DisplayName("Only the officer gains it: finance and employee still cannot read jobs")
    void nobodyElseGainsIt() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            for (UUID tenant : new UUID[] {PRE_EXISTING_TENANT, NEW_TENANT}) {
                assertThat(holds(conn, tenant, "finance", JOB_READ)).isFalse();
                assertThat(holds(conn, tenant, "employee", JOB_READ)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("The redefined seed function keeps V097's grants: a new officer still reads runs and claims")
    void earlierGrantsSurvive() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            assertThat(holds(conn, NEW_TENANT, "payroll-officer", "payroll.run.execute"))
                    .isTrue();
            assertThat(holds(conn, NEW_TENANT, "payroll-officer", "payroll.reimbursement_claim.read"))
                    .isTrue();
            assertThat(holds(conn, NEW_TENANT, "employee", "payroll.reimbursement_claim.submit_own"))
                    .isTrue();
            assertThat(count(conn, NEW_TENANT))
                    .as("one grant more than a tenant seeded by V097's function, then backfilled")
                    .isEqualTo(count(conn, PRE_EXISTING_TENANT));
        }
    }

    private static void provision(Connection conn, UUID tenantId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
        try (CallableStatement cs = conn.prepareCall("SELECT core.seed_system_roles(?)")) {
            cs.setObject(1, tenantId);
            cs.execute();
        }
    }

    private static boolean holds(Connection conn, UUID tenantId, String roleCode, String actionCode)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM core.role_action ra "
                + "JOIN core.role r ON r.id = ra.role_id AND r.tenant_id = ra.tenant_id "
                + "WHERE ra.tenant_id = ? AND r.code = ? AND ra.action_code = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, roleCode);
            ps.setString(3, actionCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int count(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT count(*) FROM core.role_action WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
