package com.infinevo.core.lop;

import static com.infinevo.core.lop.LopTestSchema.TENANT_A;
import static com.infinevo.core.lop.LopTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-18.1 acceptance test (build order criterion):
 * Two tenants on different settings derive correctly different working days and divisors from identical data.
 */
@SpringBootTest(classes = LopTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class TwoTenantBasisIT extends AbstractIntegrationTest {

    @Autowired
    private WorkingDayBasisCalculator calculator;

    @BeforeAll
    static void applySchema() throws Exception {
        LopTestSchema.apply();
        LopTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        LopTestSchema.clearAll();
    }

    @BeforeEach
    void seedData() throws SQLException {
        LopTestSchema.clearAll();

        try (Connection conn = LopTestSchema.migrationConnection()) {
            // Tenant A: FIXED_30 policy
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.lop_policy (id, tenant_id, working_day_basis, configured_days_per_month,"
                            + " weekends_payable, holidays_payable, lop_rounding, effective_from) VALUES (?, ?,"
                            + " 'FIXED_30', NULL, true, true, 'HALF_UP_2', '2026-01-01')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_A);
                ps.executeUpdate();
            }

            // Tenant B: ORG_DAYS with configured n = 26.50
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO core.lop_policy (id, tenant_id, working_day_basis, configured_days_per_month,"
                            + " weekends_payable, holidays_payable, lop_rounding, effective_from) VALUES (?, ?,"
                            + " 'ORG_DAYS', 26.50, true, true, 'HALF_UP_2', '2026-01-01')")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, TENANT_B);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName(
            "Two tenants on different bases produce different payable days and divisors for the same July 2026 period")
    void twoTenants_differentBases_produceDifferentFigures() {
        YearMonth period = YearMonth.of(2026, 7);
        UUID dummyEmployeeId = UUID.randomUUID();

        WorkingDayBasisResponse basisTenantA = calculator.basisFor(TENANT_A, period, dummyEmployeeId);
        WorkingDayBasisResponse basisTenantB = calculator.basisFor(TENANT_B, period, dummyEmployeeId);

        // Tenant A is on FIXED_30 -> exactly 30 days
        assertThat(basisTenantA.payableDays()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(basisTenantA.divisor()).isEqualByComparingTo(new BigDecimal("30.00"));

        // Tenant B is on ORG_DAYS(26.50) -> exactly 26.50 days
        assertThat(basisTenantB.payableDays()).isEqualByComparingTo(new BigDecimal("26.50"));
        assertThat(basisTenantB.divisor()).isEqualByComparingTo(new BigDecimal("26.50"));

        // Must be strictly different
        assertThat(basisTenantA.payableDays()).isNotEqualTo(basisTenantB.payableDays());
        assertThat(basisTenantA.divisor()).isNotEqualTo(basisTenantB.divisor());
        assertThat(basisTenantA.policyId()).isNotEqualTo(basisTenantB.policyId());
    }
}
