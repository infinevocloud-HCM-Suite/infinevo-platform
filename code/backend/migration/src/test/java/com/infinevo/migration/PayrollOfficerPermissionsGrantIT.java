package com.infinevo.migration;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Certification S-05 & M-15 — payroll-officer holds core.document.upload/read and core.pay_input.write/read,
 * and hr holds core.leave.manage. V177 grants these actions to new and existing tenants.
 */
@EnabledIfDockerAvailable
class PayrollOfficerPermissionsGrantIT {

    private static final String DATABASE = "infinevo_payroll_officer_grant";
    private static final UUID PLATFORM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String DOC_UPLOAD = "core.document.upload";
    private static final String DOC_READ = "core.document.read";
    private static final String PAY_INPUT_WRITE = "core.pay_input.write";
    private static final String PAY_INPUT_READ = "core.pay_input.read";
    private static final String LEAVE_MANAGE = "core.leave.manage";

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
            insertTenant(conn, NEW_TENANT, "Payroll Officer Permissions New Tenant");
        }
    }

    @Test
    @DisplayName("A tenant provisioned after V177 gets document and pay input actions on payroll-officer")
    void newOfficerHoldsDocumentAndPayInputActions() throws SQLException {
        try (Connection conn = connection()) {
            assertThat(grants(conn, NEW_TENANT, "payroll-officer", DOC_UPLOAD)).isEqualTo(1);
            assertThat(grants(conn, NEW_TENANT, "payroll-officer", DOC_READ)).isEqualTo(1);
            assertThat(grants(conn, NEW_TENANT, "payroll-officer", PAY_INPUT_WRITE))
                    .isEqualTo(1);
            assertThat(grants(conn, NEW_TENANT, "payroll-officer", PAY_INPUT_READ))
                    .isEqualTo(1);
            assertThat(grants(conn, NEW_TENANT, "hr", LEAVE_MANAGE)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Platform tenant roles are untouched by tenant actions")
    void platformTenantSkipped() throws SQLException {
        try (Connection conn = connection()) {
            assertThat(grants(conn, PLATFORM, "payroll-officer", DOC_UPLOAD)).isZero();
            assertThat(grants(conn, PLATFORM, "payroll-officer", PAY_INPUT_WRITE))
                    .isZero();
        }
    }

    private static int grants(Connection conn, UUID tenantId, String roleCode, String actionCode) throws SQLException {
        String sql = "SELECT count(*) FROM core.role_action "
                + "WHERE tenant_id = ? AND action_code = ? "
                + "  AND role_id = (SELECT id FROM core.role WHERE tenant_id = ? AND code = ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, tenantId);
            ps.setString(2, actionCode);
            ps.setObject(3, tenantId);
            ps.setString(4, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void insertTenant(Connection conn, UUID tenantId, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.tenant (tenant_id, name) VALUES (?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                jdbcUrl,
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }
}
