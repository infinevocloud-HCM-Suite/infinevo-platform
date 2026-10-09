package com.infinevo.payroll.component;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests verifying PostgreSQL Row-Level Security on salary component tables (W-26.1).
 * Asserts as app_user that Tenant A cannot read, update or deactivate Tenant B's rows.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class ComponentRlsIT extends AbstractIntegrationTest {

    @Autowired
    private EarningService earningService;

    @Autowired
    private DeductionService deductionService;

    @Autowired
    private BenefitService benefitService;

    @Autowired
    private ReimbursementService reimbursementService;

    private UUID earningB;
    private UUID deductionB;
    private UUID benefitB;
    private UUID reimbursementB;

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

        // Seed rows for Tenant B directly via migrationConnection
        earningB = seedRow("earning", TENANT_B, "BASIC", "Basic Salary", "earning_type", "FIXED");
        deductionB = seedRow("deduction", TENANT_B, "PF", "Provident Fund", "deduction_type", "STATUTORY");
        benefitB = seedRow("benefit", TENANT_B, "HEALTH", "Health Insurance", null, null);
        reimbursementB =
                seedRow("reimbursement", TENANT_B, "FUEL", "Fuel Reimbursement", "reimbursement_type", "EXPENSE");
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("Unbound app_user sees no rows in any of the four tables")
    void unboundConnectionSeesNothing() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            for (String table : new String[] {"earning", "deduction", "benefit", "reimbursement"}) {
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT count(*) FROM payroll." + table)) {
                    rs.next();
                    assertThat(rs.getInt(1))
                            .as("Table %s must return 0 rows when unbound", table)
                            .isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Tenant A raw app_user connection cannot see Tenant B's rows")
    void tenantACannotSeeTenantBRowsRawSql() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            assertThat(countVisibleById(conn, "earning", earningB)).isZero();
            assertThat(countVisibleById(conn, "deduction", deductionB)).isZero();
            assertThat(countVisibleById(conn, "benefit", benefitB)).isZero();
            assertThat(countVisibleById(conn, "reimbursement", reimbursementB)).isZero();

            // Control: Tenant B connection sees its own rows
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            assertThat(countVisibleById(conn, "earning", earningB)).isEqualTo(1);
            assertThat(countVisibleById(conn, "deduction", deductionB)).isEqualTo(1);
            assertThat(countVisibleById(conn, "benefit", benefitB)).isEqualTo(1);
            assertThat(countVisibleById(conn, "reimbursement", reimbursementB)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Tenant A cannot read Tenant B's rows through the services")
    void tenantACannotReadThroughServices() {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> earningService.get(earningB)).isInstanceOf(ComponentNotFoundException.class);
        assertThatThrownBy(() -> deductionService.get(deductionB)).isInstanceOf(ComponentNotFoundException.class);
        assertThatThrownBy(() -> benefitService.get(benefitB)).isInstanceOf(ComponentNotFoundException.class);
        assertThatThrownBy(() -> reimbursementService.get(reimbursementB))
                .isInstanceOf(ComponentNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot update Tenant B's rows through the services")
    void tenantACannotUpdateThroughServices() {
        TenantContext.set(TENANT_A);

        EarningRequest earnReq = new EarningRequest(
                "BASIC",
                "Hacked",
                null,
                "FIXED",
                CalculationType.FLAT,
                new BigDecimal("100.00"),
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                false,
                true);
        assertThatThrownBy(() -> earningService.update(earningB, earnReq))
                .isInstanceOf(ComponentNotFoundException.class);

        DeductionRequest dedReq = new DeductionRequest(
                "PF",
                "Hacked",
                null,
                "STATUTORY",
                CalculationType.FLAT,
                new BigDecimal("100.00"),
                null,
                null,
                false,
                false,
                null,
                null,
                null);
        assertThatThrownBy(() -> deductionService.update(deductionB, dedReq))
                .isInstanceOf(ComponentNotFoundException.class);

        BenefitRequest benReq = new BenefitRequest(
                "HEALTH",
                "Hacked",
                null,
                null,
                null,
                CalculationType.FLAT,
                new BigDecimal("100.00"),
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null);
        assertThatThrownBy(() -> benefitService.update(benefitB, benReq))
                .isInstanceOf(ComponentNotFoundException.class);

        ReimbursementRequest reimReq = new ReimbursementRequest(
                "FUEL",
                "Hacked",
                null,
                "EXPENSE",
                CalculationType.FLAT,
                new BigDecimal("100.00"),
                null,
                null,
                null,
                false,
                false,
                false,
                false);
        assertThatThrownBy(() -> reimbursementService.update(reimbursementB, reimReq))
                .isInstanceOf(ComponentNotFoundException.class);
    }

    @Test
    @DisplayName("Tenant A cannot deactivate Tenant B's rows through the services")
    void tenantACannotDeactivateThroughServices() {
        TenantContext.set(TENANT_A);

        assertThatThrownBy(() -> earningService.updateActive(earningB, false))
                .isInstanceOf(ComponentNotFoundException.class);
        assertThatThrownBy(() -> deductionService.updateActive(deductionB, false))
                .isInstanceOf(ComponentNotFoundException.class);
        assertThatThrownBy(() -> benefitService.updateActive(benefitB, false))
                .isInstanceOf(ComponentNotFoundException.class);
        assertThatThrownBy(() -> reimbursementService.updateActive(reimbursementB, false))
                .isInstanceOf(ComponentNotFoundException.class);
    }

    private static UUID seedRow(String table, UUID tenantId, String code, String name, String extraCol, String extraVal)
            throws SQLException {
        UUID id = UUID.randomUUID();
        String sql;
        if (extraCol != null) {
            sql = "INSERT INTO payroll." + table + " (id, tenant_id, code, name, calculation_type, " + extraCol
                    + ") VALUES (?, ?, ?, ?, 'FLAT', ?)";
        } else {
            sql = "INSERT INTO payroll." + table
                    + " (id, tenant_id, code, name, calculation_type) VALUES (?, ?, ?, ?, 'FLAT')";
        }

        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, name);
            if (extraCol != null) {
                ps.setString(5, extraVal);
            }
            ps.executeUpdate();
        }
        return id;
    }

    private static int countVisibleById(Connection conn, String table, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM payroll." + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
