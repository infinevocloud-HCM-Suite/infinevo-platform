package com.infinevo.payroll.fbp;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static com.infinevo.payroll.PayrollTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * Integration tests verifying PostgreSQL Row-Level Security on payroll.fbp (W-27.1).
 * Asserts as app_user that Tenant A cannot read, mutate or query Tenant B's plan or components.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class FbpPlanRlsIT extends AbstractIntegrationTest {

    @Autowired
    private FbpPlanService fbpPlanService;

    @Autowired
    private EarningRepository earningRepository;

    @Autowired
    private ReimbursementRepository reimbursementRepository;

    private UUID planBId;

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

        // Seed Tenant B's plan
        TenantContext.set(TENANT_B);
        FbpPlanRequest reqB = new FbpPlanRequest(
                true, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), true, true, List.of(7, 2));
        FbpPlanResponse planB = fbpPlanService.upsert(reqB);
        planBId = planB.id();

        // Seed Tenant B's FBP component
        Earning earningB = new Earning(TENANT_B, "test");
        earningB.setCode("FUEL-B");
        earningB.setName("Fuel Allowance B");
        earningB.setEarningType("ALLOWANCE");
        earningB.setCalculationType(CalculationType.FLAT);
        earningB.setDefaultValue(new BigDecimal("2500.0000"));
        earningB.setFbpComponent(true);
        earningB.setActive(true);
        earningRepository.save(earningB);

        Reimbursement reimbB = new Reimbursement(TENANT_B, "test");
        reimbB.setCode("MEAL-B");
        reimbB.setName("Meal Voucher B");
        reimbB.setCalculationType(CalculationType.FLAT);
        reimbB.setDefaultValue(new BigDecimal("3000.0000"));
        reimbB.setReimbursementType("FOOD");
        reimbB.setFbpComponent(true);
        reimbB.setActive(true);
        reimbursementRepository.save(reimbB);

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Tenant A raw app_user connection cannot select or update Tenant B's plan")
    void tenantACannotSeeOrUpdateTenantBPlanRawSql() throws SQLException {
        try (Connection conn = PayrollTestSchema.appConnection()) {
            // 1. Bound to Tenant A: count for Tenant B's plan must be 0
            PayrollTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM payroll.fbp WHERE id = ?")) {
                ps.setObject(1, planBId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1))
                            .as("Tenant A must not see Tenant B's plan row under RLS")
                            .isZero();
                }
            }

            // 2. Bound to Tenant A: update on Tenant B's plan ID must affect 0 rows
            try (PreparedStatement ps =
                    conn.prepareStatement("UPDATE payroll.fbp SET is_enabled = false WHERE id = ?")) {
                ps.setObject(1, planBId);
                int updated = ps.executeUpdate();
                assertThat(updated)
                        .as("Tenant A must not be able to update Tenant B's plan under RLS")
                        .isZero();
            }

            // 3. Control: Bound to Tenant B: can see its own plan row
            PayrollTestSchema.bindTenant(conn, TENANT_B);
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM payroll.fbp WHERE id = ?")) {
                ps.setObject(1, planBId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    @DisplayName("Tenant A cannot read or mutate Tenant B's plan through the service")
    void tenantACannotAccessTenantBPlanThroughService() {
        // Tenant A has not configured FBP yet
        TenantContext.set(TENANT_A);

        FbpPlanResponse getResponse = fbpPlanService.get();
        assertThat(getResponse.exists())
                .as("Tenant A must not receive Tenant B's plan")
                .isFalse();
        assertThat(getResponse.id()).isNull();

        // Tenant A creates its own plan
        FbpPlanRequest reqA = new FbpPlanRequest(
                true, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 15), false, false, List.of(3, 1));
        FbpPlanResponse planA = fbpPlanService.upsert(reqA);

        assertThat(planA.id()).isNotEqualTo(planBId);

        // Verify Tenant B's plan was untouched
        TenantContext.set(TENANT_B);
        FbpPlanResponse checkB = fbpPlanService.get();
        assertThat(checkB.id()).isEqualTo(planBId);
        assertThat(checkB.windowOpensOn()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(checkB.windowClosesOn()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(checkB.notifyOnRelease()).isTrue();
    }

    @Test
    @DisplayName("Tenant B's FBP components are absent from Tenant A's component list")
    void tenantBComponentsAbsentFromTenantAList() {
        // Tenant A has its own FBP earning component
        TenantContext.set(TENANT_A);
        Earning earningA = new Earning(TENANT_A, "test");
        earningA.setCode("FUEL-A");
        earningA.setName("Fuel Allowance A");
        earningA.setEarningType("ALLOWANCE");
        earningA.setCalculationType(CalculationType.FLAT);
        earningA.setDefaultValue(new BigDecimal("1000.0000"));
        earningA.setFbpComponent(true);
        earningA.setActive(true);
        earningRepository.save(earningA);

        List<FbpComponentResponse> compsA = fbpPlanService.components();

        assertThat(compsA)
                .extracting(FbpComponentResponse::code)
                .containsExactly("FUEL-A")
                .doesNotContain("FUEL-B", "MEAL-B");
    }
}
