package com.infinevo.payroll.component;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
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
 * Integration tests verifying CRUD lifecycle, active filtering, soft deletion,
 * and numeric(19,4) scale-4 precision round-trips for all component types (W-26.1).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class ComponentCrudIT extends AbstractIntegrationTest {

    @Autowired
    private EarningService earningService;

    @Autowired
    private DeductionService deductionService;

    @Autowired
    private BenefitService benefitService;

    @Autowired
    private ReimbursementService reimbursementService;

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
    }

    @AfterEach
    void tearDown() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @Test
    @DisplayName("Earning CRUD: create, round-trip scale 4, active filter, and soft delete")
    void earningLifecycle() throws SQLException {
        TenantContext.set(TENANT_A);
        BigDecimal defaultValue = new BigDecimal("45000.1234");
        BigDecimal maxLimit = new BigDecimal("90000.5678");

        EarningRequest request = new EarningRequest(
                "BASIC",
                "Basic Salary",
                "Basic",
                "FIXED",
                CalculationType.FLAT,
                defaultValue,
                null,
                maxLimit,
                "MONTHLY",
                null,
                false,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                null,
                false,
                true);

        EarningResponse created = earningService.create(request);
        assertThat(created.id()).isNotNull();
        assertThat(created.defaultValue()).isEqualByComparingTo(defaultValue);
        assertThat(created.maxLimit()).isEqualByComparingTo(maxLimit);
        assertThat(created.active()).isTrue();

        // Verify round-trip get
        EarningResponse fetched = earningService.get(created.id());
        assertThat(fetched.defaultValue()).isEqualByComparingTo(defaultValue);
        assertThat(fetched.maxLimit()).isEqualByComparingTo(maxLimit);

        // Create second earning and deactivate the first
        EarningRequest req2 = new EarningRequest(
                "HRA",
                "House Rent Allowance",
                "HRA",
                "FIXED",
                CalculationType.PERCENTAGE,
                new BigDecimal("40.0000"),
                PercentageOf.BASIC,
                null,
                "MONTHLY",
                null,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false,
                null,
                false,
                true);
        EarningResponse created2 = earningService.create(req2);

        earningService.updateActive(created.id(), false);

        List<EarningResponse> all = earningService.list(false);
        assertThat(all).hasSize(2);

        List<EarningResponse> activeOnly = earningService.list(true);
        assertThat(activeOnly).hasSize(1);
        assertThat(activeOnly.get(0).id()).isEqualTo(created2.id());

        // Soft delete created2
        earningService.delete(created2.id());

        assertThat(earningService.list(false)).hasSize(1);
        assertThatThrownBy(() -> earningService.get(created2.id())).isInstanceOf(ComponentNotFoundException.class);

        // Verify soft delete flag in DB
        assertRowDeleted("earning", created2.id());
    }

    @Test
    @DisplayName("Deduction CRUD: create, round-trip scale 4, active filter, and soft delete")
    void deductionLifecycle() throws SQLException {
        TenantContext.set(TENANT_A);
        BigDecimal defaultValue = new BigDecimal("1800.0000");

        DeductionRequest request = new DeductionRequest(
                "PF",
                "Provident Fund",
                "PF",
                "STATUTORY",
                CalculationType.FLAT,
                defaultValue,
                null,
                null,
                true,
                true,
                null,
                null,
                null);

        DeductionResponse created = deductionService.create(request);
        assertThat(created.id()).isNotNull();
        assertThat(created.defaultValue()).isEqualByComparingTo(defaultValue);

        DeductionResponse fetched = deductionService.get(created.id());
        assertThat(fetched.defaultValue()).isEqualByComparingTo(defaultValue);

        deductionService.updateActive(created.id(), false);
        assertThat(deductionService.list(true)).isEmpty();
        assertThat(deductionService.list(false)).hasSize(1);

        deductionService.delete(created.id());
        assertThat(deductionService.list(false)).isEmpty();
        assertThatThrownBy(() -> deductionService.get(created.id())).isInstanceOf(ComponentNotFoundException.class);

        assertRowDeleted("deduction", created.id());
    }

    @Test
    @DisplayName("Benefit CRUD: create, round-trip scale 4, active filter, and soft delete")
    void benefitLifecycle() throws SQLException {
        TenantContext.set(TENANT_A);
        BigDecimal defaultValue = new BigDecimal("5000.5555");
        BigDecimal maxLimit = new BigDecimal("25000.8888");

        BenefitRequest request = new BenefitRequest(
                "HEALTH",
                "Health Insurance",
                "Mediclaim",
                "GOLD",
                "INSURANCE",
                CalculationType.FLAT,
                defaultValue,
                null,
                maxLimit,
                true,
                false,
                false,
                false,
                true,
                true,
                true,
                true,
                "80D",
                "PARENTAL");

        BenefitResponse created = benefitService.create(request);
        assertThat(created.id()).isNotNull();
        assertThat(created.defaultValue()).isEqualByComparingTo(defaultValue);
        assertThat(created.maxLimit()).isEqualByComparingTo(maxLimit);

        benefitService.updateActive(created.id(), false);
        assertThat(benefitService.list(true)).isEmpty();
        assertThat(benefitService.list(false)).hasSize(1);

        benefitService.delete(created.id());
        assertThat(benefitService.list(false)).isEmpty();
        assertRowDeleted("benefit", created.id());
    }

    @Test
    @DisplayName("Reimbursement CRUD: create, round-trip scale 4, active filter, and soft delete")
    void reimbursementLifecycle() throws SQLException {
        TenantContext.set(TENANT_A);
        BigDecimal defaultValue = new BigDecimal("2200.0000");
        BigDecimal maxLimit = new BigDecimal("26400.0000");

        ReimbursementRequest request = new ReimbursementRequest(
                "FUEL",
                "Fuel Reimbursement",
                "Fuel",
                "EXPENSE",
                CalculationType.FLAT,
                defaultValue,
                null,
                maxLimit,
                "YEARLY",
                true,
                true,
                false,
                false);

        ReimbursementResponse created = reimbursementService.create(request);
        assertThat(created.id()).isNotNull();
        assertThat(created.defaultValue()).isEqualByComparingTo(defaultValue);
        assertThat(created.maxLimit()).isEqualByComparingTo(maxLimit);

        reimbursementService.updateActive(created.id(), false);
        assertThat(reimbursementService.list(true)).isEmpty();
        assertThat(reimbursementService.list(false)).hasSize(1);

        reimbursementService.delete(created.id());
        assertThat(reimbursementService.list(false)).isEmpty();
        assertRowDeleted("reimbursement", created.id());
    }

    private static void assertRowDeleted(String table, UUID id) throws SQLException {
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT is_deleted FROM payroll." + table + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("is_deleted")).isTrue();
            }
        }
    }
}
