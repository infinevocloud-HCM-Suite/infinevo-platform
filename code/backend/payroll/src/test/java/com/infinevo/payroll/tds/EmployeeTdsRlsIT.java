package com.infinevo.payroll.tds;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * PostgreSQL Row-Level Security (RLS) and index constraint tests for {@code payroll.employee_tds} (W-36.1 §7).
 *
 * <p>Covers:
 * <ul>
 *   <li>As app_user, tenant A cannot read tenant B's row</li>
 *   <li>Raw SQL INSERT with tenant B's ID under tenant A's context is refused by RLS</li>
 *   <li>Two active rows for one (tenant, employee, fy) are refused by the partial unique index</li>
 * </ul>
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class EmployeeTdsRlsIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";

    private UUID employeeA;
    private UUID employeeB;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws Exception {
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();

        employeeA = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-RLS-A", "emp_a@acme.com", "Alice", "Acme");
        employeeB = TaxDeclarationTestSchema.seedEmployee(TENANT_B, "EMP-RLS-B", "emp_b@globex.com", "Bob", "Globex");

        // Seed rows for both tenants as migration_user
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        INSERT INTO payroll.employee_tds (
                            tenant_id, employee_id, financial_year, regime, source,
                            annual_gross, annual_taxable_income, annual_tax, effective_from_period,
                            is_active, created_by, updated_by
                        ) VALUES (?, ?, ?, 'NEW', 'OFFICER', 1200000, 1000000, 120000, '2026-04', true, 'seed', 'seed')
                        """)) {
            ps.setObject(1, TENANT_A);
            ps.setObject(2, employeeA);
            ps.setString(3, FY);
            ps.executeUpdate();

            ps.setObject(1, TENANT_B);
            ps.setObject(2, employeeB);
            ps.setString(3, FY);
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("Unbound app_user connection sees zero rows in payroll.employee_tds")
    void unboundAppUserSeesZeroRows() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT count(*) FROM payroll.employee_tds")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    @DisplayName("App user bound to Tenant A sees only Tenant A rows")
    void boundAppUserSeesOnlyBoundTenantRows() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            setConnectionTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement("SELECT tenant_id FROM payroll.employee_tds");
                    ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertThat(rs.getObject(1, UUID.class)).isEqualTo(TENANT_A);
                }
                assertThat(count).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("App user bound to Tenant A sees zero rows when explicitly querying for Tenant B's tenant_id")
    void appUserBoundToTenantACannotSeeTenantBRows() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            setConnectionTenant(conn, TENANT_A);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.employee_tds WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_B);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Direct SQL INSERT with cross-tenant ID is rejected by RLS WITH CHECK policy (SQLState 42501)")
    void crossTenantInsertFailsRlsPolicy() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            setConnectionTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.employee_tds (
                        tenant_id, employee_id, financial_year, regime, source,
                        annual_gross, annual_taxable_income, annual_tax, effective_from_period,
                        is_active, created_by, updated_by
                    ) VALUES (?, ?, ?, 'NEW', 'OFFICER', 1200000, 1000000, 120000, '2026-04', true, 'bad', 'bad')
                    """)) {
                ps.setObject(1, TENANT_B);
                ps.setObject(2, employeeB);
                ps.setString(3, "2027-2028");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .satisfies(ex ->
                                assertThat(((SQLException) ex).getSQLState()).isEqualTo("42501"));
            }
        }
    }

    @Test
    @DisplayName("Two active rows for one (tenant, employee, fy) are refused by partial unique index (SQLState 23505)")
    void duplicateActiveRowRefusedByIndex() throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT INTO payroll.employee_tds (
                        tenant_id, employee_id, financial_year, regime, source,
                        annual_gross, annual_taxable_income, annual_tax, effective_from_period,
                        is_active, created_by, updated_by
                    ) VALUES (?, ?, ?, 'NEW', 'OFFICER', 1500000, 1300000, 150000, '2026-04', true, 'seed', 'seed')
                    """)) {
                // A row for (TENANT_A, employeeA, FY) is already active from setUp()
                ps.setObject(1, TENANT_A);
                ps.setObject(2, employeeA);
                ps.setString(3, FY);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .satisfies(ex ->
                                assertThat(((SQLException) ex).getSQLState()).isEqualTo("23505"));
            }
        }
    }

    private static void setConnectionTenant(Connection conn, UUID tenantId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, false)")) {
            ps.setString(1, tenantId.toString());
            ps.execute();
        }
    }
}
