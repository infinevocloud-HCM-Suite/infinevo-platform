package com.infinevo.payroll.tds;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
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
 * Row-Level Security integration tests for payroll.employee_tds (W-36.1 §7).
 * Verifies as app_user that Tenant A cannot read Tenant B's TDS row under RLS,
 * cross-tenant raw SQL insert is blocked by RLS, and two active rows for one
 * (tenant, employee, fy) are refused by the partial unique index.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class EmployeeTdsRlsIT extends AbstractIntegrationTest {

    private UUID employeeAId;
    private UUID employeeBId;
    private UUID tdsBId;

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
        TenantContext.clear();
        PayrollTestSchema.cleanTables();

        employeeAId = UUID.randomUUID();
        seedEmployee(TENANT_A, employeeAId, "EMP-RLS-01", "Alice", "Smith");

        employeeBId = UUID.randomUUID();
        seedEmployee(TENANT_B, employeeBId, "EMP-RLS-02", "Bob", "Jones");

        tdsBId = UUID.randomUUID();
        seedEmployeeTds(
                TENANT_B,
                tdsBId,
                employeeBId,
                "2026-2027",
                "NEW",
                "OFFICER",
                new BigDecimal("600000.0000"),
                new BigDecimal("550000.0000"),
                new BigDecimal("120000.0000"),
                "2026-04",
                true);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("As app_user, Tenant A cannot read Tenant B's employee_tds row under RLS")
    void tenantACannotReadTenantBRow() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.employee_tds WHERE id = ?")) {
                ps.setObject(1, tdsBId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1))
                            .as("Tenant A must see 0 rows for Tenant B's employee_tds under RLS")
                            .isZero();
                }
            }

            // Control check: bound to Tenant B
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.employee_tds WHERE id = ?")) {
                ps.setObject(1, tdsBId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    @DisplayName("A raw-SQL INSERT with Tenant B's ID under Tenant A's context is refused by RLS")
    void rawSqlInsertCrossTenant_isRefusedByRls() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.employee_tds
                        (id, tenant_id, employee_id, financial_year, regime, source, annual_gross,
                         annual_taxable_income, annual_tax, effective_from_period, is_active, created_by, updated_by)
                    VALUES (?, ?, ?, '2026-2027', 'NEW', 'OFFICER', 500000.0000, 450000.0000, 50000.0000, '2026-04', true, 'attacker', 'attacker')
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_B);
                ps.setObject(3, employeeBId);

                assertThatThrownBy(ps::executeUpdate)
                        .as("Tenant A inserting a row with tenant_id B must be blocked by RLS")
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    @DisplayName("Two active rows for one (tenant, employee, fy) are refused by partial unique index")
    void twoActiveRowsForOneTenantEmployeeFy_isRefusedByUniqueIndex() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            UUID id1 = UUID.randomUUID();
            seedEmployeeTds(
                    TENANT_A,
                    id1,
                    employeeAId,
                    "2026-2027",
                    "NEW",
                    "OFFICER",
                    new BigDecimal("600000.0000"),
                    new BigDecimal("550000.0000"),
                    new BigDecimal("120000.0000"),
                    "2026-04",
                    true);

            UUID id2 = UUID.randomUUID();
            assertThatThrownBy(() -> seedEmployeeTds(
                            TENANT_A,
                            id2,
                            employeeAId,
                            "2026-2027",
                            "OLD",
                            "OFFICER",
                            new BigDecimal("600000.0000"),
                            new BigDecimal("550000.0000"),
                            new BigDecimal("100000.0000"),
                            "2026-04",
                            true))
                    .as(
                            "Second active row for (tenant, employee, fy) must violate uk_employee_tds_tenant_employee_fy_active")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uk_employee_tds_tenant_employee_fy_active");
        }
    }

    private static void seedEmployeeTds(
            UUID tenantId,
            UUID id,
            UUID employeeId,
            String fy,
            String regime,
            String source,
            BigDecimal gross,
            BigDecimal taxable,
            BigDecimal tax,
            String effectivePeriod,
            boolean active)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.employee_tds
                            (id, tenant_id, employee_id, financial_year, regime, source,
                             annual_gross, annual_taxable_income, annual_tax, effective_from_period,
                             is_active, created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'system', 'system')
                        """)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, employeeId);
            ps.setString(4, fy);
            ps.setString(5, regime);
            ps.setString(6, source);
            ps.setBigDecimal(7, gross);
            ps.setBigDecimal(8, taxable);
            ps.setBigDecimal(9, tax);
            ps.setString(10, effectivePeriod);
            ps.setBoolean(11, active);
            ps.executeUpdate();
        }
    }

    private static void seedEmployee(UUID tenantId, UUID employeeId, String code, String first, String last)
            throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO core.employee
                            (id, tenant_id, employee_number, first_name, last_name, gender, date_of_joining, status,
                             created_by, updated_by)
                        VALUES (?, ?, ?, ?, ?, 'MALE', DATE '2023-04-01', 'ACTIVE', 'test', 'test')
                        """)) {
            ps.setObject(1, employeeId);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, first);
            ps.setString(5, last);
            ps.executeUpdate();
        }
    }
}
