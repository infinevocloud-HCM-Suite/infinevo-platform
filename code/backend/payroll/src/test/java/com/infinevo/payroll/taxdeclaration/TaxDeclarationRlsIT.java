package com.infinevo.payroll.taxdeclaration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test proving PostgreSQL Row-Level Security (RLS) isolation on tax declaration tables (W-32.1, spec §7).
 *
 * <p>Validates:
 * <ul>
 *   <li>As {@code app_user}, an unbound connection sees zero rows</li>
 *   <li>Tenant A cannot see Tenant B's declaration window or header</li>
 *   <li>Tenant A cannot read, edit, submit, or lock Tenant B's declaration</li>
 * </ul>
 */
@SpringBootTest(classes = PayrollTestApp.class)
class TaxDeclarationRlsIT extends AbstractIntegrationTest {

    private static final String FY = "2025-2026";

    @Autowired
    private TaxDeclarationWindowService windowService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    private UUID employeeA;
    private UUID employeeB;

    @BeforeAll
    static void applySchema() throws Exception {
        TaxDeclarationTestSchema.apply();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        TaxDeclarationTestSchema.clearAll();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        TaxDeclarationTestSchema.seedTenants();
        TaxDeclarationTestSchema.clearDeclarations();

        // Seed employee in Tenant A and Tenant B
        employeeA = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_A, "EMP-A", "a@acme.com", "Alice", "Acme");
        employeeB = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_B, "EMP-B", "b@globex.com", "Bob", "Globex");

        // Seed window for Tenant A and Tenant B
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        windowService.upsert(
                FY,
                new TaxDeclarationWindowRequest(
                        LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30), false, "NEW", true, true, false, false));

        // Seed declaration for Tenant A
        taxDeclarationService.read(employeeA, FY);

        TenantContext.set(TaxDeclarationTestSchema.TENANT_B);
        windowService.upsert(
                FY,
                new TaxDeclarationWindowRequest(
                        LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 15), false, "OLD", true, true, false, false));

        // Seed declaration for Tenant B
        taxDeclarationService.read(employeeB, FY);

        TenantContext.clear();
    }

    @AfterEach
    void cleanUp() throws SQLException {
        TaxDeclarationTestSchema.clearDeclarations();
        TenantContext.clear();
    }

    @Test
    @DisplayName("An unbound app_user connection sees zero rows in tax declaration tables")
    void unboundAppUserSeesZeroRows() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection();
                Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM payroll.income_tax_declaration")) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("income_tax_declaration unbound count")
                        .isZero();
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM payroll.employee_investment_declaration")) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("employee_investment_declaration unbound count")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("Raw app_user connection bound to Tenant A sees only Tenant A rows")
    void boundAppUserSeesOnlyBoundTenantRows() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement("SELECT tenant_id FROM payroll.income_tax_declaration");
                    ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject(1, UUID.class)).isEqualTo(TaxDeclarationTestSchema.TENANT_A);
                assertThat(rs.next()).isFalse(); // Tenant B's window row is hidden by RLS
            }

            try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT tenant_id, employee_id FROM payroll.employee_investment_declaration");
                    ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject(1, UUID.class)).isEqualTo(TaxDeclarationTestSchema.TENANT_A);
                assertThat(rs.getObject(2, UUID.class)).isEqualTo(employeeA);
                assertThat(rs.next()).isFalse(); // Tenant B's declaration row is hidden by RLS
            }
        }
    }

    @Test
    @DisplayName("Tenant A cannot access or mutate Tenant B's employee declaration via service")
    void tenantACannotAccessOrMutateTenantBDeclaration() {
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);

        // Spec §7: tenant A cannot create or edit a header against tenant B's employee. The
        // tenant-scoped employee lookup is what refuses it, and nothing is written.
        assertThatThrownBy(() ->
                        taxDeclarationService.save(employeeB, FY, new TaxDeclarationRequest("OLD", true, true, false)))
                .isInstanceOf(EmployeeService.NotFoundException.class);
        assertThatThrownBy(() -> taxDeclarationService.read(employeeB, FY))
                .isInstanceOf(EmployeeService.NotFoundException.class);
        assertThatThrownBy(() -> taxDeclarationService.submit(employeeB, FY))
                .isInstanceOf(EmployeeService.NotFoundException.class);
        assertThatThrownBy(() -> taxDeclarationService.lock(employeeB, FY))
                .isInstanceOf(EmployeeService.NotFoundException.class);

        assertThatThrownBy(() -> taxDeclarationService.require(employeeB, FY))
                .isInstanceOf(DeclarationNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A has written no header for tenant B's employee after the refused calls")
    void noHeaderWrittenAcrossTenants() throws SQLException {
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        try {
            assertThatThrownBy(() -> taxDeclarationService.read(employeeB, FY))
                    .isInstanceOf(EmployeeService.NotFoundException.class);
        } finally {
            TenantContext.clear();
        }
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM payroll.employee_investment_declaration WHERE tenant_id = ? AND employee_id = ?")) {
            ps.setObject(1, TaxDeclarationTestSchema.TENANT_A);
            ps.setObject(2, employeeB);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("no tenant A header against tenant B's employee")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("Direct database INSERT with cross-tenant ID is rejected by RLS WITH CHECK policy")
    void databaseLevelCrossTenantInsertFailsRlsPolicy() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payroll.employee_investment_declaration "
                    + "(id, tenant_id, employee_id, financial_year, tax_regime, status, is_locked) "
                    + "VALUES (?, ?, ?, ?, 'OLD', 'DRAFT', false)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TaxDeclarationTestSchema.TENANT_B);
                ps.setObject(3, employeeB);
                ps.setString(4, FY);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .satisfies(ex ->
                                assertThat(((SQLException) ex).getSQLState()).isEqualTo("42501"));
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
