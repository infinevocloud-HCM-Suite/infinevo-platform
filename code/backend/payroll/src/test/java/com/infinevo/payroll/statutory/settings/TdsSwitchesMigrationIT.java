package com.infinevo.payroll.statutory.settings;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * D-141: V190 adds the company TDS switch table and the employee flag, seeding the flag off for anyone whose
 * personal details say "Eligible for Full Tax Exemption", and creating a profile with the flag off for
 * such an employee who has none.
 *
 * <p>Runs against real Postgres in one transaction: the two objects are put back to their pre-V190 shape,
 * profiles are seeded, V190 is executed, the flag and the new table are read, and everything is rolled back.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class TdsSwitchesMigrationIT extends AbstractIntegrationTest {

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("V190 seeds is_tds_applicable from the full tax exemption flag, on otherwise, and adds tds_setting")
    void seedsEmployeeFlagFromExemption() throws Exception {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            conn.setAutoCommit(false);
            try {
                restorePreV190Shape(conn);
                UUID exempt = seedEmployee(conn, TENANT_A, "TDS-MIG-1", Boolean.TRUE);
                UUID notExempt = seedEmployee(conn, TENANT_A, "TDS-MIG-2", Boolean.FALSE);
                UUID noPersonal = seedEmployee(conn, TENANT_B, "TDS-MIG-3", null);
                UUID exemptNoProfile = seedEmployee(conn, TENANT_B, "TDS-MIG-4", Boolean.TRUE, false);
                UUID notExemptNoProfile = seedEmployee(conn, TENANT_A, "TDS-MIG-5", Boolean.FALSE, false);

                PayrollTestSchema.executeResource(conn, "db/migration/payroll/V190__tds_switches.sql");

                assertThat(tdsApplicable(conn, exempt)).isFalse();
                assertThat(tdsApplicable(conn, notExempt)).isTrue();
                assertThat(tdsApplicable(conn, noPersonal)).isTrue();

                // An exempt employee with no profile would otherwise read as applicable (no profile
                // means true), so V190 creates one with the flag off and every other column at its
                // default, in the employee's own tenant.
                assertThat(tdsApplicable(conn, exemptNoProfile)).isFalse();
                try (PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT tenant_id, is_eligible_for_pf, is_eligible_for_pt, is_eligible_for_lwf,
                               is_eligible_for_esi, is_eligible_for_eps, contributes_eps_on_higher_wages,
                               is_director, created_by, updated_by
                        FROM payroll.employee_statutory_profile WHERE employee_id = ?
                        """)) {
                    ps.setObject(1, exemptNoProfile);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getObject("tenant_id", UUID.class)).isEqualTo(TENANT_B);
                        for (int column = 2; column <= 8; column++) {
                            assertThat(rs.getBoolean(column))
                                    .as("column %d at its default", column)
                                    .isFalse();
                        }
                        assertThat(rs.getString("created_by")).isEqualTo("V190");
                        assertThat(rs.getString("updated_by")).isEqualTo("V190");
                        assertThat(rs.next()).as("exactly one profile row").isFalse();
                    }
                }
                assertThat(profileCount(conn, notExemptNoProfile))
                        .as("no profile created for an employee who is not exempt")
                        .isZero();
                assertThat(profileCount(conn, exempt))
                        .as("an existing profile is updated, not duplicated")
                        .isEqualTo(1);

                assertThat(PayrollTestSchema.tableExists(conn, "payroll", "tds_setting"))
                        .isTrue();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.tds_setting (tenant_id) VALUES (?) RETURNING is_enabled")) {
                    ps.setObject(1, TENANT_A);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getBoolean(1))
                                .as("company TDS on by default")
                                .isTrue();
                    }
                }
                try (Statement st = conn.createStatement();
                        ResultSet rs = st.executeQuery("SELECT c.relrowsecurity FROM pg_class c"
                                + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                                + " WHERE n.nspname = 'payroll' AND c.relname = 'tds_setting'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean(1))
                            .as("row-level security on tds_setting")
                            .isTrue();
                }
            } finally {
                conn.rollback();
            }
        }
    }

    private static void restorePreV190Shape(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("DROP TABLE payroll.tds_setting");
            st.execute("ALTER TABLE payroll.employee_statutory_profile DROP COLUMN is_tds_applicable");
        }
    }

    /** An employee with a statutory profile and, unless {@code exempt} is null, a personal row. */
    private static UUID seedEmployee(Connection conn, UUID tenantId, String number, Boolean exempt)
            throws SQLException {
        return seedEmployee(conn, tenantId, number, exempt, true);
    }

    /** As above; {@code withProfile} false leaves the employee with no statutory profile row. */
    private static UUID seedEmployee(Connection conn, UUID tenantId, String number, Boolean exempt, boolean withProfile)
            throws SQLException {
        UUID employeeId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO core.employee
                    (id, tenant_id, employee_number, first_name, last_name, date_of_joining, status,
                     created_by, updated_by)
                VALUES (?, ?, ?, 'Test', 'Employee', DATE '2025-04-01', 'ACTIVE', 'test', 'test')
                """)) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, number);
            ps.executeUpdate();
        }
        if (exempt != null) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.employee_personal (tenant_id, employee_id, is_eligible_for_full_tax_exemption)"
                            + " VALUES (?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, employeeId);
                ps.setBoolean(3, exempt);
                ps.executeUpdate();
            }
        }
        if (withProfile) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO payroll.employee_statutory_profile (tenant_id, employee_id) VALUES (?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, employeeId);
                ps.executeUpdate();
            }
        }
        return employeeId;
    }

    private static int profileCount(Connection conn, UUID employeeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT count(*) FROM payroll.employee_statutory_profile WHERE employee_id = ?")) {
            ps.setObject(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static boolean tdsApplicable(Connection conn, UUID employeeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT is_tds_applicable FROM payroll.employee_statutory_profile WHERE employee_id = ?")) {
            ps.setObject(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getBoolean(1);
            }
        }
    }
}
