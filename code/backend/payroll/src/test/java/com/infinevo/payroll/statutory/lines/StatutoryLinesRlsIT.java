package com.infinevo.payroll.statutory.lines;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Row-Level Security integration test for statutory lines (W-31.3).
 * Spec §7: as app_user, tenant A reads none of tenant B's rows.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class StatutoryLinesRlsIT extends AbstractIntegrationTest {

    private UUID ctcStructureB;
    private UUID epfLineBId;
    private UUID esiLineBId;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        PayrollTestSchema.cleanTables();

        UUID employeeB = UUID.randomUUID();
        seedEmployee(TENANT_B, employeeB, "EMP-B01", "Bob", "Jones");

        ctcStructureB = UUID.randomUUID();
        seedCtcStructure(TENANT_B, ctcStructureB, employeeB);

        epfLineBId = UUID.randomUUID();
        seedCtcEpfComponent(
                TENANT_B,
                epfLineBId,
                ctcStructureB,
                "EPF_EMPLOYEE",
                "EMPLOYEE",
                new BigDecimal("15000.0000"),
                new BigDecimal("12.0000"),
                new BigDecimal("1800.0000"));

        esiLineBId = UUID.randomUUID();
        seedCtcEsiComponent(
                TENANT_B,
                esiLineBId,
                ctcStructureB,
                "ESI_EMPLOYEE",
                "EMPLOYEE",
                new BigDecimal("15000.0000"),
                new BigDecimal("0.7500"),
                new BigDecimal("112.5000"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("As app_user, Tenant A reads none of Tenant B's statutory rows")
    void tenantACannotReadTenantBStatutoryRows() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            // Bind Tenant A
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            assertThat(countVisibleById(conn, "payroll.ctc_epf_component", epfLineBId))
                    .as("Tenant A cannot see Tenant B's EPF component row")
                    .isZero();

            assertThat(countVisibleById(conn, "payroll.ctc_esi_component", esiLineBId))
                    .as("Tenant A cannot see Tenant B's ESI component row")
                    .isZero();

            // Control: Tenant B sees its own rows
            PayrollTestSchema.bindTenant(conn, TENANT_B);

            assertThat(countVisibleById(conn, "payroll.ctc_epf_component", epfLineBId))
                    .as("Tenant B sees its own EPF component row")
                    .isEqualTo(1);

            assertThat(countVisibleById(conn, "payroll.ctc_esi_component", esiLineBId))
                    .as("Tenant B sees its own ESI component row")
                    .isEqualTo(1);

            // Unbound session sees no rows
            PayrollTestSchema.clearTenant(conn);

            assertThat(countVisibleById(conn, "payroll.ctc_epf_component", epfLineBId))
                    .as("Unbound tenant cannot see EPF row")
                    .isZero();

            assertThat(countVisibleById(conn, "payroll.ctc_esi_component", esiLineBId))
                    .as("Unbound tenant cannot see ESI row")
                    .isZero();
        }
    }

    @Test
    @DisplayName("As app_user, Tenant A cannot mutate Tenant B's statutory rows")
    void tenantACannotMutateTenantBStatutoryRows() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            // Bind Tenant A
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE payroll.ctc_epf_component SET rate = 99.0000 WHERE id = ?")) {
                ps.setObject(1, epfLineBId);
                int updated = ps.executeUpdate();
                assertThat(updated)
                        .as("Tenant A cannot update Tenant B EPF line")
                        .isZero();
            }

            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE payroll.ctc_esi_component SET rate = 99.0000 WHERE id = ?")) {
                ps.setObject(1, esiLineBId);
                int updated = ps.executeUpdate();
                assertThat(updated)
                        .as("Tenant A cannot update Tenant B ESI line")
                        .isZero();
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM payroll.ctc_epf_component WHERE id = ?")) {
                ps.setObject(1, epfLineBId);
                int deleted = ps.executeUpdate();
                assertThat(deleted)
                        .as("Tenant A cannot delete Tenant B EPF line")
                        .isZero();
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM payroll.ctc_esi_component WHERE id = ?")) {
                ps.setObject(1, esiLineBId);
                int deleted = ps.executeUpdate();
                assertThat(deleted)
                        .as("Tenant A cannot delete Tenant B ESI line")
                        .isZero();
            }
        }
    }

    private static int countVisibleById(Connection conn, String table, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM " + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId, String code, String first, String last)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.employee (id, tenant_id, employee_number, first_name, last_name, work_email, date_of_joining, status) "
                                + "VALUES (?, ?, ?, ?, ?, ?, '2026-01-01', 'ACTIVE') ON CONFLICT DO NOTHING")) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, first);
            ps.setString(5, last);
            ps.setString(6, code.toLowerCase() + "@example.com");
            ps.executeUpdate();
        }
    }

    private static void seedCtcStructure(UUID tenantId, UUID ctcStructureId, UUID employeeId) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.ctc_structure (id, tenant_id, employee_id, effective_from, annual_ctc, monthly_ctc, is_cancelled, created_by, updated_by) "
                                + "VALUES (?, ?, ?, '2026-01-01', 300000.0000, 25000.0000, false, 'system', 'system')")) {
            ps.setObject(1, ctcStructureId);
            ps.setObject(2, tenantId);
            ps.setObject(3, employeeId);
            ps.executeUpdate();
        }
    }

    private static void seedCtcEpfComponent(
            UUID tenantId,
            UUID id,
            UUID ctcStructureId,
            String code,
            String share,
            BigDecimal wageBase,
            BigDecimal rate,
            BigDecimal monthlyAmount)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.ctc_epf_component (id, tenant_id, ctc_structure_id, component_code, share, wage_base, rate, monthly_amount, annual_amount, is_included_in_ctc, created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, false, 'system', 'system')")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, ctcStructureId);
            ps.setString(4, code);
            ps.setString(5, share);
            ps.setBigDecimal(6, wageBase);
            ps.setBigDecimal(7, rate);
            ps.setBigDecimal(8, monthlyAmount);
            ps.setBigDecimal(9, monthlyAmount.multiply(BigDecimal.valueOf(12)));
            ps.executeUpdate();
        }
    }

    private static void seedCtcEsiComponent(
            UUID tenantId,
            UUID id,
            UUID ctcStructureId,
            String code,
            String share,
            BigDecimal wageBase,
            BigDecimal rate,
            BigDecimal monthlyAmount)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payroll.ctc_esi_component (id, tenant_id, ctc_structure_id, component_code, share, wage_base, rate, monthly_amount, annual_amount, is_included_in_ctc, created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, false, 'system', 'system')")) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, ctcStructureId);
            ps.setString(4, code);
            ps.setString(5, share);
            ps.setBigDecimal(6, wageBase);
            ps.setBigDecimal(7, rate);
            ps.setBigDecimal(8, monthlyAmount);
            ps.setBigDecimal(9, monthlyAmount.multiply(BigDecimal.valueOf(12)));
            ps.executeUpdate();
        }
    }
}
