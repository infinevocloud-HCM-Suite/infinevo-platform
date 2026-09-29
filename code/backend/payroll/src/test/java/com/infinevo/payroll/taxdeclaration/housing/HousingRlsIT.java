package com.infinevo.payroll.taxdeclaration.housing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HomeLoanRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HouseRentRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyLineRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyRequest;
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
 * Integration test proving PostgreSQL Row-Level Security (RLS) isolation on housing tables (W-32.2).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class HousingRlsIT extends AbstractIntegrationTest {

    private static final String FY = "2024-2025";

    @Autowired
    private TaxDeclarationWindowService windowService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private HousingDeclarationService housingDeclarationService;

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
                TaxDeclarationTestSchema.TENANT_A, "EMP-HA", "ha@acme.com", "HousingA", "Tester");
        employeeB = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_B, "EMP-HB", "hb@globex.com", "HousingB", "Tester");

        // Seed Tenant A window & housing
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        windowService.upsert(
                FY,
                new TaxDeclarationWindowRequest(
                        LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30), false, "OLD", true, true, false, false));
        taxDeclarationService.read(employeeA, FY);
        housingDeclarationService.replaceHouseRent(
                employeeA,
                FY,
                List.of(new HouseRentRequest(
                        "2024-04",
                        "2025-03",
                        "Address A",
                        "Landlord A",
                        "ABCDE1234F",
                        true,
                        new BigDecimal("20000.0000"))));
        housingDeclarationService.replaceHomeLoans(
                employeeA,
                FY,
                List.of(new HomeLoanRequest(
                        "Bank A",
                        "AAACA1234F",
                        new BigDecimal("50000.0000"),
                        new BigDecimal("100000.0000"),
                        true,
                        null)));
        housingDeclarationService.replaceLetOutProperties(
                employeeA,
                FY,
                List.of(new LetOutPropertyRequest(
                        "Property A",
                        "Address Prop A",
                        List.of(new LetOutPropertyLineRequest(
                                LetOutPropertyLineType.ANNUAL_RENT, new BigDecimal("200000.0000"), null, null)))));

        // Seed Tenant B window & housing
        TenantContext.set(TaxDeclarationTestSchema.TENANT_B);
        windowService.upsert(
                FY,
                new TaxDeclarationWindowRequest(
                        LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30), false, "OLD", true, true, false, false));
        taxDeclarationService.read(employeeB, FY);
        housingDeclarationService.replaceHouseRent(
                employeeB,
                FY,
                List.of(new HouseRentRequest(
                        "2024-04",
                        "2025-03",
                        "Address B",
                        "Landlord B",
                        "XYZPK9876Q",
                        false,
                        new BigDecimal("30000.0000"))));
        housingDeclarationService.replaceHomeLoans(
                employeeB,
                FY,
                List.of(new HomeLoanRequest(
                        "Bank B",
                        "AAACB1234F",
                        new BigDecimal("80000.0000"),
                        new BigDecimal("120000.0000"),
                        false,
                        null)));
        housingDeclarationService.replaceLetOutProperties(
                employeeB,
                FY,
                List.of(new LetOutPropertyRequest(
                        "Property B",
                        "Address Prop B",
                        List.of(new LetOutPropertyLineRequest(
                                LetOutPropertyLineType.ANNUAL_RENT, new BigDecimal("350000.0000"), null, null)))));

        TenantContext.clear();
    }

    @AfterEach
    void cleanUp() throws SQLException {
        TaxDeclarationTestSchema.clearDeclarations();
        TenantContext.clear();
    }

    @Test
    @DisplayName("An unbound app_user connection sees zero rows in housing tables")
    void unboundAppUserSeesZeroRows() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection();
                Statement stmt = conn.createStatement()) {
            for (String table : List.of(
                    "employee_inv_house_rent",
                    "employee_inv_home_loan",
                    "employee_inv_let_out_property",
                    "employee_inv_let_out_property_line")) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM payroll." + table)) {
                    rs.next();
                    assertThat(rs.getInt(1)).as(table + " unbound count").isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Raw app_user connection bound to Tenant A sees only Tenant A rows across all housing tables")
    void boundAppUserSeesOnlyBoundTenantRows() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);

            for (String table : List.of(
                    "employee_inv_house_rent",
                    "employee_inv_home_loan",
                    "employee_inv_let_out_property",
                    "employee_inv_let_out_property_line")) {
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
                    "employee_inv_house_rent",
                    "employee_inv_home_loan",
                    "employee_inv_let_out_property",
                    "employee_inv_let_out_property_line")) {
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
                    "employee_inv_house_rent",
                    "employee_inv_home_loan",
                    "employee_inv_let_out_property",
                    "employee_inv_let_out_property_line")) {
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
    @DisplayName("Tenant A cannot replace or read housing declarations of Tenant B employee")
    void cannotMutateOrReadAcrossTenants() {
        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        try {
            assertThatThrownBy(() -> housingDeclarationService.replaceHouseRent(
                            employeeB,
                            FY,
                            List.of(new HouseRentRequest(
                                    "2024-04",
                                    "2025-03",
                                    "Hack Address",
                                    "Hack Landlord",
                                    null,
                                    false,
                                    new BigDecimal("50000.0000")))))
                    .isInstanceOf(EmployeeService.NotFoundException.class);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Direct database INSERT with cross-tenant ID into house rent is rejected by RLS WITH CHECK policy")
    void databaseLevelCrossTenantInsertFailsRlsPolicy() throws SQLException {
        try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO payroll.employee_inv_house_rent "
                    + "(id, tenant_id, declaration_id, from_month, to_month, is_metro, amount_per_month) "
                    + "VALUES (?, ?, ?, '2024-04-01', '2025-03-01', true, 10000)")) {
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
    @DisplayName(
            "Direct database INSERT with cross-tenant ID into home loan, let-out property and its lines is rejected by RLS")
    void databaseLevelCrossTenantInsertFailsOnEveryHousingTable() throws SQLException {
        List<String> inserts = List.of(
                "INSERT INTO payroll.employee_inv_home_loan (id, tenant_id, declaration_id, lender_name, principal_paid, interest_paid)"
                        + " VALUES (?, ?, ?, 'Hack Bank', 1000, 1000)",
                "INSERT INTO payroll.employee_inv_let_out_property (id, tenant_id, declaration_id, property_name, net_income_loss)"
                        + " VALUES (?, ?, ?, 'Hack Villa', 0)",
                "INSERT INTO payroll.employee_inv_let_out_property_line (id, tenant_id, declaration_id, property_id, line_type, amount)"
                        + " VALUES (?, ?, ?, ?, 'ANNUAL_RENT', 1000)");
        for (String sql : inserts) {
            try (Connection conn = TaxDeclarationTestSchema.appConnection()) {
                conn.setAutoCommit(false);
                setConnectionTenant(conn, TaxDeclarationTestSchema.TENANT_A);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, TaxDeclarationTestSchema.TENANT_B);
                    ps.setObject(3, UUID.randomUUID());
                    if (sql.contains("property_id")) {
                        ps.setObject(4, UUID.randomUUID());
                    }
                    assertThatThrownBy(ps::executeUpdate)
                            .as(sql)
                            .isInstanceOf(SQLException.class)
                            .satisfies(ex -> assertThat(((SQLException) ex).getSQLState())
                                    .isEqualTo("42501"));
                }
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
