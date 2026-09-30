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
 * needs {@code core.job.read}. V059 grants it through a trigger of its own for a tenant provisioned
 * afterwards and through {@code core.grant_payrun_job_actions} for the tenants that already exist;
 * {@code core.seed_system_roles} is left as it was.
 */
@EnabledIfDockerAvailable
class PayRunJobReadSeedIT extends AbstractIntegrationTest {

    private static final String JOB_READ = "core.job.read";
    private static final UUID NEW_TENANT = UUID.randomUUID();

    @BeforeAll
    static void applySchemaAndProvision() throws Exception {
        PayRunTestSchema.apply();
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            insertTenant(conn, NEW_TENANT, "Job Read New Tenant");
        }
    }

    @Test
    @DisplayName("A tenant provisioned after V059 gets core.job.read on its payroll officer, from the trigger alone")
    void newOfficerHoldsIt() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            assertThat(grants(conn, NEW_TENANT, "payroll-officer", JOB_READ)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Only the officer gains it: finance, hr, manager and employee still cannot read jobs")
    void nobodyElseGainsIt() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            for (String role : new String[] {"finance", "hr", "manager", "employee"}) {
                assertThat(grants(conn, NEW_TENANT, role, JOB_READ)).as(role).isZero();
            }
        }
    }

    @Test
    @DisplayName("The officer keeps the grants core.seed_system_roles gives: the function was not replaced")
    void seedFunctionGrantsSurvive() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            assertThat(grants(conn, NEW_TENANT, "payroll-officer", "payroll.run.execute"))
                    .isEqualTo(1);
            assertThat(grants(conn, NEW_TENANT, "payroll-officer", "payroll.reimbursement_claim.read"))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("The backfill function grants a tenant that lacks it, and running it twice changes nothing")
    void backfillGrantsAnExistingTenantOnce() throws SQLException {
        UUID existing = UUID.randomUUID();
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            insertTenant(conn, existing, "Job Read Existing Tenant");
            // What a tenant provisioned before V059 looks like: system roles, no job grant.
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM core.role_action WHERE tenant_id = ? AND action_code = ? AND role_id IN "
                            + "(SELECT id FROM core.role WHERE tenant_id = ? AND code = 'payroll-officer')")) {
                ps.setObject(1, existing);
                ps.setString(2, JOB_READ);
                ps.setObject(3, existing);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
            assertThat(grants(conn, existing, "payroll-officer", JOB_READ)).isZero();

            backfill(conn, existing);
            backfill(conn, existing);

            assertThat(grants(conn, existing, "payroll-officer", JOB_READ)).isEqualTo(1);
            assertThat(grants(conn, existing, "finance", JOB_READ)).isZero();
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
        try (CallableStatement cs = conn.prepareCall("SELECT core.grant_payrun_job_actions(?)")) {
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
}
