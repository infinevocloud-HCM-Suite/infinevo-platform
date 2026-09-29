package com.infinevo.payroll.statutory.pt;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
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
 * Acceptance integration test for professional tax override lifecycle (W-31.2, spec section 7).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class ProfessionalTaxOverrideIT extends AbstractIntegrationTest {

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

        // Seed an active work location in Karnataka for Tenant A
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.work_location (tenant_id, code, name, state, state_code, is_active) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, TENANT_A);
            ps.setString(2, "BLR");
            ps.setString(3, "Bangalore Office");
            ps.setString(4, "Karnataka");
            ps.setString(5, "KA");
            ps.setBoolean(6, true);
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName(
            "Acceptance: Full override lifecycle — GET REFERENCE, PUT OVERRIDE, resolve override, DELETE, resolve reference")
    void fullOverrideLifecycle() throws Exception {
        long initialRefSlabCount = getReferencePtSlabCount();

        TenantContext.set(TENANT_A);

        // 1. Initial GET shows REFERENCE
        PtStateResponse stateResponse = professionalTaxService.getStateForTenant("KA");
        assertThat(stateResponse.source()).isEqualTo(PtSource.REFERENCE);
        assertThat(stateResponse.stateCode()).isEqualTo("KA");
        assertThat(stateResponse.slabs()).isNotEmpty();

        // Initial resolve returns standard reference statutory amount (200.0000 for gross 30,000)
        Money initialPt = professionalTaxService.resolve(
                TENANT_A, "KA", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(initialPt).isEqualTo(Money.of("200.0000"));

        // 2. PUT an override with a different amount (e.g. 250.0000 for gross >= 25,000)
        PtOverrideRequest overrideReq = new PtOverrideRequest(
                "KA-REG-9999",
                LocalDate.of(2024, 4, 1),
                List.of(
                        new PtSlabDto(
                                new BigDecimal("0.0000"),
                                new BigDecimal("24999.0000"),
                                new BigDecimal("0.0000"),
                                false,
                                null),
                        new PtSlabDto(new BigDecimal("24999.0000"), null, new BigDecimal("250.0000"), false, null)));

        PtStateResponse putResponse = professionalTaxService.setOverride("KA", overrideReq);
        assertThat(putResponse.source()).isEqualTo(PtSource.OVERRIDE);
        assertThat(putResponse.registrationNumber()).isEqualTo("KA-REG-9999");
        assertThat(putResponse.slabs()).hasSize(2);

        // Resolve now returns the override amount (250.0000)
        Money overridePt = professionalTaxService.resolve(
                TENANT_A, "KA", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(overridePt).isEqualTo(Money.of("250.0000"));

        // pt_history contains OVERRIDE_SET with before and after slabs
        List<PtHistoryResponse> historyAfterSet = professionalTaxService.getHistory("KA");
        assertThat(historyAfterSet).hasSize(1);
        PtHistoryResponse setEntry = historyAfterSet.get(0);
        assertThat(setEntry.operation()).isEqualTo(PtHistoryOperation.OVERRIDE_SET);
        assertThat(setEntry.beforeSlabs()).isNotEmpty();
        assertThat(setEntry.afterSlabs()).hasSize(2);

        // 3. DELETE reset override back to reference slabs
        professionalTaxService.resetOverride("KA");

        // GET now shows REFERENCE again
        PtStateResponse resetResponse = professionalTaxService.getStateForTenant("KA");
        assertThat(resetResponse.source()).isEqualTo(PtSource.REFERENCE);

        // Resolve returns statutory reference amount (200.0000)
        Money resetPt = professionalTaxService.resolve(
                TENANT_A, "KA", Money.of("30000.0000"), "male", LocalDate.of(2025, 5, 31));
        assertThat(resetPt).isEqualTo(Money.of("200.0000"));

        // pt_history has OVERRIDE_RESET written
        List<PtHistoryResponse> historyAfterReset = professionalTaxService.getHistory("KA");
        assertThat(historyAfterReset).hasSize(2);
        PtHistoryResponse resetEntry = historyAfterReset.get(0); // newest first
        assertThat(resetEntry.operation()).isEqualTo(PtHistoryOperation.OVERRIDE_RESET);
        assertThat(resetEntry.beforeSlabs()).hasSize(2); // previous override slabs

        // reference.pt_slab row count unchanged throughout
        long finalRefSlabCount = getReferencePtSlabCount();
        assertThat(finalRefSlabCount).isEqualTo(initialRefSlabCount);
    }

    private long getReferencePtSlabCount() throws Exception {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM reference.pt_slab");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
