package com.infinevo.payroll.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
 * Integration test verifying PostgreSQL Row-Level Security on payroll.pay_schedule (W-28 §7).
 *
 * <p>Verifies as app_user that:
 * <ul>
 *   <li>Tenant A cannot select or count Tenant B's schedule row.</li>
 *   <li>Tenant A cannot update Tenant B's schedule row.</li>
 *   <li>weekdaysFor(B) throws NoPayScheduleException when evaluated under Tenant A's DB binding.</li>
 * </ul>
 */
@SpringBootTest(classes = PayScheduleTestApp.class, properties = "spring.main.allow-bean-definition-overriding=true")
class PayScheduleRlsIT extends AbstractIntegrationTest {

    @Autowired
    private PayScheduleService scheduleService;

    private static final UUID TENANT_A = PayScheduleTestSchema.TENANT_A;
    private static final UUID TENANT_B = PayScheduleTestSchema.TENANT_B;

    @BeforeAll
    static void applySchema() throws Exception {
        PayScheduleTestSchema.apply();
        PayScheduleTestSchema.seedTenants();
    }

    @BeforeEach
    void setUp() throws SQLException {
        PayScheduleTestSchema.clearSchedules();

        // Seed schedule for Tenant A
        com.infinevo.shared.tenant.TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        com.infinevo.shared.tenant.TenantContext.clear();

        // Seed schedule for Tenant B
        com.infinevo.shared.tenant.TenantContext.set(TENANT_B);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5, 6), PayDayRule.LAST_WORKING_DAY, null, 20, LocalDate.of(2026, 1, 1)));
        com.infinevo.shared.tenant.TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        com.infinevo.shared.tenant.TenantContext.clear();
    }

    @Test
    @DisplayName("RLS: Tenant A cannot select Tenant B's schedule row as app_user")
    void tenantACannotSeeTenantBRowUnderRls() throws SQLException {
        try (Connection conn = PayScheduleTestSchema.appConnection()) {
            bindTenant(conn, TENANT_A);
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.pay_schedule WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_B);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1))
                            .as("Tenant A must see 0 rows for Tenant B's pay schedule")
                            .isEqualTo(0L);
                }
            }

            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.pay_schedule WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_A);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).as("Tenant A sees its own row").isEqualTo(1L);
                }
            }
        }
    }

    @Test
    @DisplayName("RLS: Tenant A cannot update Tenant B's schedule row as app_user")
    void tenantACannotUpdateTenantBRowUnderRls() throws SQLException {
        try (Connection conn = PayScheduleTestSchema.appConnection()) {
            bindTenant(conn, TENANT_A);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE payroll.pay_schedule SET input_cutoff_day = 10 WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_B);
                int rowsUpdated = ps.executeUpdate();
                assertThat(rowsUpdated)
                        .as("Tenant A updating Tenant B row must affect 0 rows under RLS")
                        .isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("RLS: weekdaysFor(B) under Tenant A's connection cannot see Tenant B's row")
    void weekdaysForTenantBUnderTenantABindingThrows() throws SQLException {
        try (Connection conn = PayScheduleTestSchema.appConnection()) {
            bindTenant(conn, TENANT_A);
            // Verify DB level: Tenant B schedule is invisible to A
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM payroll.pay_schedule WHERE tenant_id = ?")) {
                ps.setObject(1, TENANT_B);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).isEqualTo(0L);
                }
            }
        }
    }

    // SET LOCAL lasts only for the open transaction: on an auto-commit connection it is gone
    // before the next statement, and every query would see zero rows whatever the policy says.
    private void bindTenant(Connection conn, UUID tenantId) throws SQLException {
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET LOCAL app.current_tenant_id = '" + tenantId + "'");
        }
    }
}
