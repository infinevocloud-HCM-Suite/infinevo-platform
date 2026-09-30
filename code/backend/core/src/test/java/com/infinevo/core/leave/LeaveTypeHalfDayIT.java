package com.infinevo.core.leave;

import static com.infinevo.core.leave.LeaveTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

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
 * W-16.1, spec section 7 &amp; 8 — {@code LeaveTypeHalfDayIT}.
 *
 * <p>Assures BUG-003 fix at the source:
 * <ul>
 *   <li>{@code 2.5} days survives a write and read on every day-count column in {@code core.leave_policy}.
 *   <li>Checks schema precision (numeric(10,2)) and value roundtrip without integer truncation.
 * </ul>
 */
@SpringBootTest(classes = LeaveTestApp.class)
@org.springframework.test.context.ContextConfiguration(
        initializers = com.infinevo.shared.test.PostgresTestContainerInitializer.class)
class LeaveTypeHalfDayIT extends AbstractIntegrationTest {

    @Autowired
    private LeaveTypeService leaveTypeService;

    @BeforeAll
    static void applySchema() throws Exception {
        LeaveTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        LeaveTestSchema.clearAll();
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContext.clear();
        LeaveTestSchema.seedTenants();
        LeaveTestSchema.clearAll();
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("2.5 days survives a write and read on every day-count column without truncation")
    void halfDaysSurviveRoundtripOnAllDayCountColumns() throws SQLException {
        TenantContext.set(TENANT_A);

        LeaveTypeResponse type = leaveTypeService.createLeaveType(
                new LeaveTypeRequest("Casual Leave", "CL", true, LeaveUnit.DAYS, true, LocalDate.of(2026, 1, 1), null));

        BigDecimal halfDayValue = new BigDecimal("2.50");

        LeavePolicyResponse policy = leaveTypeService.setPolicy(
                type.id(),
                new LeavePolicyRequest(
                        halfDayValue, // annualDays
                        true,
                        AccrualFrequency.MONTHLY,
                        halfDayValue, // accrualUnits
                        false,
                        null,
                        true,
                        halfDayValue, // carryForwardCap
                        6,
                        false,
                        null,
                        null,
                        false,
                        false,
                        ExceedBalanceMode.YEAR_END_LIMIT,
                        halfDayValue, // exceedBalanceLimitDays
                        false,
                        halfDayValue, // maxDaysPerApplication
                        null,
                        LocalDate.of(2026, 1, 1),
                        List.of()));

        // Service level roundtrip verification
        assertThat(policy.annualDays()).isEqualByComparingTo(halfDayValue);
        assertThat(policy.accrualUnits()).isEqualByComparingTo(halfDayValue);
        assertThat(policy.carryForwardCap()).isEqualByComparingTo(halfDayValue);
        assertThat(policy.exceedBalanceLimitDays()).isEqualByComparingTo(halfDayValue);
        assertThat(policy.maxDaysPerApplication()).isEqualByComparingTo(halfDayValue);

        // Raw database read to guarantee PostgreSQL stored decimal places and did not truncate to 2
        try (Connection conn = LeaveTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            LeaveTestSchema.bindTenant(conn, TENANT_A);

            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    SELECT annual_days, accrual_units, carry_forward_cap, exceed_balance_limit_days, max_days_per_application
                    FROM core.leave_policy
                    WHERE id = ?
                    """)) {
                ps.setObject(1, policy.id());
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBigDecimal("annual_days")).isEqualByComparingTo(halfDayValue);
                    assertThat(rs.getBigDecimal("accrual_units")).isEqualByComparingTo(halfDayValue);
                    assertThat(rs.getBigDecimal("carry_forward_cap")).isEqualByComparingTo(halfDayValue);
                    assertThat(rs.getBigDecimal("exceed_balance_limit_days")).isEqualByComparingTo(halfDayValue);
                    assertThat(rs.getBigDecimal("max_days_per_application")).isEqualByComparingTo(halfDayValue);
                }
            }
        }
    }

    @Test
    @DisplayName("Information schema confirms numeric(10,2) precision on all leave day count columns")
    void verifyInformationSchemaColumnTypes() throws SQLException {
        try (Connection conn = LeaveTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        """
                        SELECT column_name, data_type, numeric_precision, numeric_scale
                        FROM information_schema.columns
                        WHERE table_schema = 'core'
                          AND table_name = 'leave_policy'
                          AND column_name IN ('annual_days', 'accrual_units', 'carry_forward_cap', 'exceed_balance_limit_days', 'max_days_per_application')
                        ORDER BY column_name
                        """);
                ResultSet rs = ps.executeQuery()) {

            int count = 0;
            while (rs.next()) {
                count++;
                String col = rs.getString("column_name");
                String type = rs.getString("data_type");
                int precision = rs.getInt("numeric_precision");
                int scale = rs.getInt("numeric_scale");

                assertThat(type).as("Column " + col + " must be numeric").isEqualTo("numeric");
                assertThat(precision)
                        .as("Column " + col + " precision must be 10")
                        .isEqualTo(10);
                assertThat(scale).as("Column " + col + " scale must be 2").isEqualTo(2);
            }
            assertThat(count).isEqualTo(5);
        }
    }
}
