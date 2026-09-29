package com.infinevo.payroll.taxdeclaration.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.payroll.taxdeclaration.summary.dto.OtherIncomeRequest;
import com.infinevo.payroll.taxdeclaration.summary.dto.TaxSummaryFigures;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
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
 * Integration test proving PostgreSQL Row-Level Security (RLS) isolation on summary and other income tables (W-32.4).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class TaxSummaryRlsIT extends AbstractIntegrationTest {

    private static final String FY = "2024-2025";

    @Autowired
    private TaxDeclarationWindowService windowService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private OtherIncomeService otherIncomeService;

    @Autowired
    private TaxSummaryService taxSummaryService;

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

        // Seed employees
        employeeA = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_A, "EMP-SA", "sa@acme.com", "SumA", "Tester");
        employeeB = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_B, "EMP-SB", "sb@globex.com", "SumB", "Tester");

        // Tenant A seeding
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        windowService.upsert(
                FY,
                new TaxDeclarationWindowRequest(
                        LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30), false, "OLD", true, true, false, false));
        taxDeclarationService.read(employeeA, FY);
        otherIncomeService.replace(
                employeeA,
                FY,
                List.of(new OtherIncomeRequest(OtherIncomeKind.SAVINGS_INTEREST, null, new BigDecimal("10000.0000"))));
        taxSummaryService.summary(employeeA, FY);

        // Tenant B seeding
        TenantContext.set(TaxDeclarationTestSchema.TENANT_B);
        windowService.upsert(
                FY,
                new TaxDeclarationWindowRequest(
                        LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30), false, "OLD", true, true, false, false));
        taxDeclarationService.read(employeeB, FY);
        otherIncomeService.replace(
                employeeB,
                FY,
                List.of(new OtherIncomeRequest(OtherIncomeKind.FD_INTEREST, null, new BigDecimal("25000.0000"))));
        taxSummaryService.summary(employeeB, FY);

        TenantContext.clear();
    }

    @AfterEach
    void cleanUp() throws SQLException {
        TaxDeclarationTestSchema.clearDeclarations();
        TenantContext.clear();
    }

    @Test
    @DisplayName("An unbound app_user connection sees zero rows in other income and summary tables")
    void unboundAppUserSeesZeroRows() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection();
                Statement stmt = conn.createStatement()) {
            for (String table : List.of("employee_inv_other_income", "employee_inv_tax_summary")) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM payroll." + table)) {
                    rs.next();
                    assertThat(rs.getInt(1)).as(table + " unbound count").isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Raw app_user connection bound to Tenant A sees only Tenant A rows")
    void boundAppUserSeesOnlyBoundTenantRows() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);

            for (String table : List.of("employee_inv_other_income", "employee_inv_tax_summary")) {
                try (PreparedStatement ps = conn.prepareStatement("SELECT tenant_id FROM payroll." + table);
                        ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        assertThat(rs.getObject(1, UUID.class))
                                .as(table + " tenant_id must match bound tenant")
                                .isEqualTo(TaxDeclarationTestSchema.TENANT_A);
                    }
                    assertThat(count)
                            .as(table + " must return positive row count for bound Tenant A")
                            .isPositive();
                }
            }
        }
    }

    @Test
    @DisplayName("Database contains rows for both tenants, but app_user bound to Tenant A cannot see Tenant B rows")
    void appUserBoundToTenantACannotSeeTenantBRows() throws SQLException {
        // Migration user (bypassing RLS) sees rows for both Tenant A and Tenant B
        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                Statement stmt = conn.createStatement()) {
            for (String table : List.of("employee_inv_other_income", "employee_inv_tax_summary")) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(DISTINCT tenant_id) FROM payroll." + table)) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as(table + " contains data from both tenants in migration view")
                            .isEqualTo(2);
                }
            }
        }

        // App user bound to Tenant A sees zero rows when explicitly querying for Tenant B's tenant_id
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);
            for (String table : List.of("employee_inv_other_income", "employee_inv_tax_summary")) {
                try (PreparedStatement ps =
                        conn.prepareStatement("SELECT count(*) FROM payroll." + table + " WHERE tenant_id = ?")) {
                    ps.setObject(1, TaxDeclarationTestSchema.TENANT_B);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getInt(1))
                                .as("Tenant A connection must see 0 rows of Tenant B in " + table)
                                .isZero();
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Tenant A cannot record tax summary figures against Tenant B's declaration")
    void recordAcrossTenantsThrowsAndAffectsZeroRows() throws SQLException {
        UUID declBId;
        try {
            TenantContext.set(TaxDeclarationTestSchema.TENANT_B);
            declBId = taxDeclarationService.require(employeeB, FY).getId();
        } finally {
            TenantContext.clear();
        }

        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        try {
            TaxSummaryFigures figures = new TaxSummaryFigures(
                    new BigDecimal("1000000.0000"),
                    new BigDecimal("950000.0000"),
                    new BigDecimal("100000.0000"),
                    new BigDecimal("50000.0000"),
                    new BigDecimal("50000.0000"),
                    new BigDecimal("30000.0000"),
                    new BigDecimal("20000.0000"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    6);

            assertThatThrownBy(() -> taxSummaryService.record(declBId, "NEW", figures))
                    .isInstanceOf(DeclarationNotFoundException.class);
        } finally {
            TenantContext.clear();
        }

        try (Connection conn = TaxDeclarationTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM payroll.employee_inv_tax_summary WHERE tenant_id = ? AND declaration_id = ?")) {
            ps.setObject(1, TaxDeclarationTestSchema.TENANT_A);
            ps.setObject(2, declBId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("No cross-tenant summary row created")
                        .isZero();
            }
        }
    }

    @Test
    @DisplayName("Direct database INSERT with cross-tenant ID into other income is rejected by RLS WITH CHECK policy")
    void databaseLevelCrossTenantInsertFailsRlsPolicy() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payroll.employee_inv_other_income "
                    + "(id, tenant_id, declaration_id, kind, amount) "
                    + "VALUES (?, ?, ?, 'SAVINGS_INTEREST', 5000)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TaxDeclarationTestSchema.TENANT_B);
                ps.setObject(3, UUID.randomUUID());
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .satisfies(ex ->
                                assertThat(((SQLException) ex).getSQLState()).isEqualTo("42501"));
            }
        }
    }

    @Test
    @DisplayName("Direct database INSERT with cross-tenant ID into tax summary is rejected by RLS WITH CHECK policy")
    void databaseLevelCrossTenantInsertIntoTaxSummaryFailsRlsPolicy() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payroll.employee_inv_tax_summary "
                    + "(id, tenant_id, declaration_id, regime) VALUES (?, ?, ?, 'NEW')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TaxDeclarationTestSchema.TENANT_B);
                ps.setObject(3, UUID.randomUUID());
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
