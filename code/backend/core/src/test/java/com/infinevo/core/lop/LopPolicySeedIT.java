package com.infinevo.core.lop;

import static com.infinevo.core.lop.LopTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.tenant.TenantRequest;
import com.infinevo.core.tenant.TenantResponse;
import com.infinevo.core.tenant.TenantService;
import com.infinevo.core.tenant.TenantServiceImpl;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * W-18.1 seed and tenant provisioning tests (W-18.1 §6, §7, D-60).
 */
@SpringBootTest(classes = LopTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class LopPolicySeedIT extends AbstractIntegrationTest {

    @Autowired
    private WorkingDayBasisCalculator calculator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void applySchema() throws Exception {
        LopTestSchema.apply();
        LopTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        LopTestSchema.clearAll();
    }

    @Test
    @DisplayName("Every seeded dev tenant has exactly one ACTUAL_DAYS policy after migration")
    void seededTenant_hasOneActualDaysPolicy() throws SQLException {
        try (Connection conn = LopTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT working_day_basis, weekends_payable, holidays_payable, effective_from "
                                + "FROM core.lop_policy WHERE tenant_id = ?")) {
            ps.setObject(1, TENANT_A);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("working_day_basis")).isEqualTo("ACTUAL_DAYS");
                assertThat(rs.getBoolean("weekends_payable")).isTrue();
                assertThat(rs.getBoolean("holidays_payable")).isTrue();
                assertThat(rs.getString("effective_from")).isEqualTo("1900-01-01");
                // Exactly one row
                assertThat(rs.next()).isFalse();
            }
        }

        // basisFor on seeded tenant returns calendar days of the month and does not throw
        YearMonth july = YearMonth.of(2026, 7);
        WorkingDayBasisResponse response = calculator.basisFor(TENANT_A, july, UUID.randomUUID());
        assertThat(response.payableDays()).isEqualByComparingTo(new BigDecimal("31.00"));
        assertThat(response.divisor()).isEqualByComparingTo(new BigDecimal("31.00"));
    }

    @Test
    @DisplayName("A newly provisioned tenant gets an ACTUAL_DAYS policy created in the same transaction")
    void newlyProvisionedTenant_getsDefaultPolicyAtomically() throws SQLException {
        TenantService tenantService = new TenantServiceImpl(jdbcTemplate);
        String uniqueName = "Provisioned Tenant " + UUID.randomUUID();
        TenantResponse provisioned = tenantService.provisionTenant(
                new TenantRequest(uniqueName, "IN", "Asia/Kolkata", (short) 4, Set.of(PlatformModule.PAYROLL)));

        UUID newTenantId = provisioned.tenantId();

        try (Connection conn = LopTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT working_day_basis, weekends_payable, holidays_payable, effective_from "
                                + "FROM core.lop_policy WHERE tenant_id = ?")) {
            ps.setObject(1, newTenantId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("working_day_basis")).isEqualTo("ACTUAL_DAYS");
                assertThat(rs.getBoolean("weekends_payable")).isTrue();
                assertThat(rs.getBoolean("holidays_payable")).isTrue();
                assertThat(rs.getString("effective_from")).isEqualTo("1900-01-01");
                assertThat(rs.next()).isFalse();
            }
        }

        // basisFor on the newly provisioned tenant returns month days without throwing
        YearMonth feb = YearMonth.of(2026, 2);
        WorkingDayBasisResponse response = calculator.basisFor(newTenantId, feb, UUID.randomUUID());
        assertThat(response.payableDays()).isEqualByComparingTo(new BigDecimal("28.00"));
        assertThat(response.divisor()).isEqualByComparingTo(new BigDecimal("28.00"));
    }
}
