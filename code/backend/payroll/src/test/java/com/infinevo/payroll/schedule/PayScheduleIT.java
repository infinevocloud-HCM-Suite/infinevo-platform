package com.infinevo.payroll.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-28 integration tests for {@link PayScheduleService} and {@link PayPeriodService} (W-28 §7).
 *
 * <p>Runs against a real PostgreSQL container. Verifies:
 * <ul>
 *   <li>GET before any PUT returns {@code exists: false} and writes no row to the database.</li>
 *   <li>PUT twice leaves exactly one row (upsert).</li>
 *   <li>period for a month before firstPeriodStart is 400.</li>
 * </ul>
 */
@SpringBootTest(classes = PayScheduleTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class PayScheduleIT extends AbstractIntegrationTest {

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayPeriodService periodService;

    private final UUID tenantId = PayScheduleTestSchema.TENANT_A;

    @BeforeAll
    static void applySchema() throws Exception {
        PayScheduleTestSchema.apply();
        PayScheduleTestSchema.seedTenants();
    }

    @BeforeEach
    void setUp() throws SQLException {
        PayScheduleTestSchema.clearSchedules();
        // Bind tenant context for this thread
        com.infinevo.shared.tenant.TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        com.infinevo.shared.tenant.TenantContext.clear();
    }

    @Test
    @DisplayName("GET before any PUT returns exists=false and writes no row to the database")
    void getBeforePut_returnsMissingResponse_writesNoRow() throws SQLException {
        PayScheduleResponse response = scheduleService.get();

        assertThat(response.exists()).isFalse();
        assertThat(response.id()).isNull();
        assertThat(response.tenantId()).isEqualTo(tenantId);

        // Assert no row was written to the database
        try (Connection conn = PayScheduleTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT COUNT(*) FROM payroll.pay_schedule WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getLong(1)).as("No row should be written by GET").isEqualTo(0L);
            }
        }
    }

    @Test
    @DisplayName("PUT creates a row; second PUT with same tenant_id updates rather than inserting a second row")
    void putTwice_leavesExactlyOneRow() throws SQLException {
        PayScheduleRequest req1 = new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1));
        scheduleService.upsert(req1);

        PayScheduleRequest req2 = new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5, 6), PayDayRule.LAST_WORKING_DAY, null, 20, LocalDate.of(2026, 1, 1));
        PayScheduleResponse response = scheduleService.upsert(req2);

        assertThat(response.exists()).isTrue();
        assertThat(response.workingDays()).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
        assertThat(response.payDayRule()).isEqualTo(PayDayRule.LAST_WORKING_DAY);
        assertThat(response.inputCutoffDay()).isEqualTo(20);

        // Exactly one row
        try (Connection conn = PayScheduleTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("SELECT COUNT(*) FROM payroll.pay_schedule WHERE tenant_id = ?")) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getLong(1)).as("Exactly one row after two PUTs").isEqualTo(1L);
            }
        }
    }

    @Test
    @DisplayName("period before firstPeriodStart returns 400 (IllegalArgumentException)")
    void period_beforeFirstPeriodStart_throws400() {
        // Set up schedule starting 2026-06-01
        PayScheduleRequest req = new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 6, 1));
        scheduleService.upsert(req);

        // Request period 2026-05 which is before 2026-06
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> periodService.periodFor(java.time.YearMonth.of(2026, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before first configured period");
    }
}
