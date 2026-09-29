package com.infinevo.payroll.statutory.pt;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Row-Level Security integration test across tenants for professional tax overrides (W-31.2, spec section 7).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class ProfessionalTaxRlsIT extends AbstractIntegrationTest {

    @Autowired
    private ProfessionalTaxService professionalTaxService;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws Exception {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();

        // Seed work locations for both tenants
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.work_location (tenant_id, code, name, state, state_code, is_active) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "BLR-A");
            ps.setString(3, "Bangalore Office A");
            ps.setString(4, "Karnataka");
            ps.setString(5, "KA");
            ps.setBoolean(6, true);
            ps.executeUpdate();

            ps.setObject(1, TENANT_B);
            ps.setString(2, "BLR-B");
            ps.setString(3, "Bangalore Office B");
            ps.setString(4, "Karnataka");
            ps.setString(5, "KA");
            ps.setBoolean(6, true);
            ps.executeUpdate();
        }

        // Set override for Tenant A only (KA -> 250.0000)
        TenantContext.set(TENANT_A);
        professionalTaxService.setOverride(
                "KA",
                new PtOverrideRequest(
                        "REG-A",
                        LocalDate.of(2024, 4, 1),
                        List.of(
                                new PtSlabDto(
                                        new BigDecimal("0.0000"),
                                        new BigDecimal("24999.0000"),
                                        new BigDecimal("0.0000"),
                                        false,
                                        null),
                                new PtSlabDto(
                                        new BigDecimal("24999.0000"), null, new BigDecimal("250.0000"), false, null))));
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("Tenant B's resolve ignores Tenant A's override and uses statutory reference slabs")
    void tenantBIgnoresTenantAOverride() {
        // Tenant A resolves to override amount (250)
        TenantContext.set(TENANT_A);
        Money ptA = professionalTaxService.resolve(
                TENANT_A, "KA", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(ptA).isEqualTo(Money.of("250.0000"));

        // Tenant B resolves to statutory reference amount (200)
        TenantContext.set(TENANT_B);
        Money ptB = professionalTaxService.resolve(
                TENANT_B, "KA", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(ptB).isEqualTo(Money.of("200.0000"));

        // When Tenant B is bound, attempting to resolve for Tenant A cannot read A's override due to RLS
        Money ptCross = professionalTaxService.resolve(
                TENANT_A, "KA", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(ptCross).isEqualTo(Money.of("200.0000"));
    }

    @Test
    @DisplayName("Tenant B cannot read Tenant A's history")
    void tenantBCannotReadTenantAHistory() {
        TenantContext.set(TENANT_B);
        List<PtHistoryResponse> bHistory = professionalTaxService.getHistory("KA");
        assertThat(bHistory).isEmpty();

        TenantContext.set(TENANT_A);
        List<PtHistoryResponse> aHistory = professionalTaxService.getHistory("KA");
        assertThat(aHistory).hasSize(1);
    }

    @Test
    @DisplayName("Direct SQL via app_user enforces RLS across tenants")
    void directAppUserRlsEnforcement() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            // Unbound session sees 0 rows
            PayrollTestSchema.clearTenant(conn);
            assertCount(conn, "SELECT count(*) FROM payroll.org_pt_override", 0);
            assertCount(conn, "SELECT count(*) FROM payroll.org_pt_override_slab", 0);
            assertCount(conn, "SELECT count(*) FROM payroll.pt_history", 0);

            // Bound to Tenant B sees 0 rows
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            assertCount(conn, "SELECT count(*) FROM payroll.org_pt_override", 0);
            assertCount(conn, "SELECT count(*) FROM payroll.org_pt_override_slab", 0);
            assertCount(conn, "SELECT count(*) FROM payroll.pt_history", 0);

            // Bound to Tenant A sees A's rows
            PayrollTestSchema.bindTenant(conn, TENANT_A);
            assertCount(conn, "SELECT count(*) FROM payroll.org_pt_override", 1);
            assertCount(conn, "SELECT count(*) FROM payroll.org_pt_override_slab", 2);
            assertCount(conn, "SELECT count(*) FROM payroll.pt_history", 1);
        }
    }

    private void assertCount(Connection conn, String sql, int expected) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(expected);
        }
    }
}
