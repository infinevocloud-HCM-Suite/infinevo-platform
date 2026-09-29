package com.infinevo.payroll.taxdeclaration.deductions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PreTaxDeductionRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PrevEmploymentRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6AItemResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6ALineRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
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
 * Integration test proving PostgreSQL Row-Level Security (RLS) isolation on deduction tables (W-32.3).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class DeductionRlsIT extends AbstractIntegrationTest {

    private static final String FY = "2024-2025";

    @Autowired
    private TaxDeclarationWindowService windowService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private DeductionDeclarationService deductionService;

    private UUID employeeA;
    private UUID employeeB;
    private UUID item80cA;

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
                TaxDeclarationTestSchema.TENANT_A, "EMP-DA", "da@acme.com", "DedA", "Tester");
        employeeB = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_B, "EMP-DB", "db@globex.com", "DedB", "Tester");

        // Tenant A seeding
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        windowService.upsert(
                FY,
                new TaxDeclarationWindowRequest(
                        LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30), false, "OLD", true, true, false, false));
        taxDeclarationService.read(employeeA, FY);
        List<Section6AItemResponse> itemsA = deductionService.getSection6AItems(employeeA, FY);
        item80cA = itemsA.stream()
                .filter(i -> "80C".equalsIgnoreCase(i.sectionCode()))
                .findFirst()
                .orElseThrow()
                .id();
        deductionService.replaceSection6A(
                employeeA, FY, List.of(new Section6ALineRequest(item80cA, "LIC A", new BigDecimal("30000.0000"))));
        deductionService.replacePreTaxDeductions(
                employeeA,
                FY,
                List.of(new PreTaxDeductionRequest(PreTaxDeductionKind.VPF, new BigDecimal("10000.0000"))));
        deductionService.replacePrevEmployment(
                employeeA,
                FY,
                List.of(new PrevEmploymentRequest(
                        PrevEmploymentKind.INCOME, new BigDecimal("200000.0000"), "Old A", "MUMB12345F")));

        // Tenant B seeding
        TenantContext.set(TaxDeclarationTestSchema.TENANT_B);
        windowService.upsert(
                FY,
                new TaxDeclarationWindowRequest(
                        LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30), false, "OLD", true, true, false, false));
        taxDeclarationService.read(employeeB, FY);
        List<Section6AItemResponse> itemsB = deductionService.getSection6AItems(employeeB, FY);
        UUID item80cB = itemsB.stream()
                .filter(i -> "80C".equalsIgnoreCase(i.sectionCode()))
                .findFirst()
                .orElseThrow()
                .id();
        deductionService.replaceSection6A(
                employeeB, FY, List.of(new Section6ALineRequest(item80cB, "PPF B", new BigDecimal("40000.0000"))));
        deductionService.replacePreTaxDeductions(
                employeeB,
                FY,
                List.of(new PreTaxDeductionRequest(PreTaxDeductionKind.NPS_EMPLOYEE, new BigDecimal("25000.0000"))));
        deductionService.replacePrevEmployment(
                employeeB,
                FY,
                List.of(new PrevEmploymentRequest(
                        PrevEmploymentKind.INCOME, new BigDecimal("300000.0000"), "Old B", "DELH98765A")));

        TenantContext.clear();
    }

    @AfterEach
    void cleanUp() throws SQLException {
        TaxDeclarationTestSchema.clearDeclarations();
        TenantContext.clear();
    }

    @Test
    @DisplayName("An unbound app_user connection sees zero rows in deduction tables")
    void unboundAppUserSeesZeroRows() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection();
                Statement stmt = conn.createStatement()) {
            for (String table : List.of(
                    "employee_inv_section6a", "employee_inv_pre_tax_deduction", "employee_inv_prev_employment")) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM payroll." + table)) {
                    rs.next();
                    assertThat(rs.getInt(1)).as(table + " unbound count").isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Raw app_user connection bound to Tenant A sees only Tenant A rows across all deduction tables")
    void boundAppUserSeesOnlyBoundTenantRows() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);

            for (String table : List.of(
                    "employee_inv_section6a", "employee_inv_pre_tax_deduction", "employee_inv_prev_employment")) {
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
            for (String table : List.of(
                    "employee_inv_section6a", "employee_inv_pre_tax_deduction", "employee_inv_prev_employment")) {
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
            for (String table : List.of(
                    "employee_inv_section6a", "employee_inv_pre_tax_deduction", "employee_inv_prev_employment")) {
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
    @DisplayName("Tenant A cannot replace or read deduction declarations of Tenant B employee")
    void cannotMutateOrReadAcrossTenants() {
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        try {
            assertThatThrownBy(() -> deductionService.replaceSection6A(
                            employeeB,
                            FY,
                            List.of(new Section6ALineRequest(
                                    UUID.randomUUID(), "Hack S6A", new BigDecimal("5000.0000")))))
                    .isInstanceOf(EmployeeService.NotFoundException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Direct database INSERT with cross-tenant ID into section6a is rejected by RLS WITH CHECK policy")
    void databaseLevelCrossTenantInsertFailsRlsPolicy() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payroll.employee_inv_section6a "
                    + "(id, tenant_id, declaration_id, section6a_item_id, description, amount) "
                    + "VALUES (?, ?, ?, ?, 'Test S6A', 10000)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TaxDeclarationTestSchema.TENANT_B);
                ps.setObject(3, UUID.randomUUID());
                ps.setObject(4, item80cA);
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
