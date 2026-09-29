package com.infinevo.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.worker.InfinevoWorkerApplication;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-20.2 §7 — Integration test for Row-Level Security on reminder rules:
 * <ul>
 *   <li>A sweep bound to tenant A evaluates no rule of tenant B</li>
 *   <li>Tenant A's session cannot read or mutate tenant B's reminder rules under RLS</li>
 *   <li>Cross-tenant insertion is blocked by RLS WITH CHECK policy</li>
 * </ul>
 */
@SpringBootTest(
        classes = InfinevoWorkerApplication.class,
        properties = {"BREVO_API_KEY=test-api-key", "infinevo.cache.enabled=false"})
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, NotificationWorkerTestSchema.Initializer.class})
class ReminderRuleRlsIT extends AbstractIntegrationTest {

    @Autowired
    private ReminderEvaluator reminderEvaluator;

    @MockBean
    private NotificationService notificationService;

    private UUID tenantA;
    private UUID tenantB;
    private UUID employeeA;
    private UUID employeeB;
    private UUID ruleIdA;
    private UUID ruleIdB;

    /**
     * The one moment the rules are due at and the sweep runs at. Read once, so a run that crosses
     * midnight UTC between seeding and sweeping cannot see a different day from the rule's.
     */
    private Instant now;

    @BeforeAll
    static void initSchema() {
        NotificationWorkerTestSchema.jdbcUrl();
    }

    @BeforeEach
    void seed() throws SQLException {
        TenantContext.clear();
        tenantA = NotificationWorkerTestSchema.insertTenant("Tenant A " + UUID.randomUUID(), "UTC");
        tenantB = NotificationWorkerTestSchema.insertTenant("Tenant B " + UUID.randomUUID(), "UTC");

        employeeA = NotificationWorkerTestSchema.insertEmployee(tenantA, "EMP-A-" + UUID.randomUUID(), null);
        employeeB = NotificationWorkerTestSchema.insertEmployee(tenantB, "EMP-B-" + UUID.randomUUID(), null);

        now = Instant.now();
        DayOfWeek today = now.atZone(ZoneId.of("UTC")).getDayOfWeek();
        // Due from midnight: whatever time of day this runs, the send time has passed.
        Time sqlTime = Time.valueOf(LocalTime.MIDNIGHT);

        ruleIdA = NotificationWorkerTestSchema.insertReminderRule(
                tenantA, "TIMESHEET_REMINDER", "SUBJECT", "WEEKLY", 0, today.getValue(), sqlTime, true);

        ruleIdB = NotificationWorkerTestSchema.insertReminderRule(
                tenantB, "TIMESHEET_REMINDER", "SUBJECT", "WEEKLY", 0, today.getValue(), sqlTime, true);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A sweep bound to tenant A evaluates no rule of tenant B")
    void sweepBoundToTenantAEvaluatesNoRuleOfTenantB() throws SQLException {
        // Run evaluation bound exclusively to Tenant A
        TenantContext.set(tenantA);
        try {
            int executed = reminderEvaluator.evaluateTenant(tenantA, ZoneId.of("UTC"), now);
            assertThat(executed).isEqualTo(1);
        } finally {
            TenantContext.clear();
        }

        // Verify: Notification composed for Employee A, never for Employee B
        verify(notificationService, atLeastOnce())
                .compose(eq(NotificationEvent.TIMESHEET_REMINDER), eq(employeeA), any());
        verify(notificationService, never()).compose(eq(NotificationEvent.TIMESHEET_REMINDER), eq(employeeB), any());

        // Verify in DB: Rule A was executed (last_executed_at is set, repeat_count = 1)
        try (Connection conn = NotificationWorkerTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT last_executed_at, repeat_count FROM core.reminder_rule WHERE id = ?")) {
            ps.setObject(1, ruleIdA);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp("last_executed_at")).isNotNull();
                assertThat(rs.getInt("repeat_count")).isEqualTo(1);
            }
        }

        // Verify in DB: Rule B was NOT touched (last_executed_at is null, repeat_count = 0)
        try (Connection conn = NotificationWorkerTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT last_executed_at, repeat_count FROM core.reminder_rule WHERE id = ?")) {
            ps.setObject(1, ruleIdB);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp("last_executed_at")).isNull();
                assertThat(rs.getInt("repeat_count")).isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("RLS hides tenant B's reminder rules from database session bound to tenant A")
    void rlsHidesOtherTenantRulesFromSession() throws SQLException {
        try (Connection conn = NotificationWorkerTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            NotificationWorkerTestSchema.bindTenant(conn, tenantA);

            // Tenant A can see its own rule
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.reminder_rule WHERE id = ?")) {
                ps.setObject(1, ruleIdA);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).isEqualTo(1);
                }
            }

            // Tenant A cannot see Tenant B's rule
            try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.reminder_rule WHERE id = ?")) {
                ps.setObject(1, ruleIdB);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).isZero();
                }
            }

            // Total count for tenant A sees only 1 rule
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT count(*) FROM core.reminder_rule WHERE tenant_id = ?")) {
                ps.setObject(1, tenantB);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getLong(1)).isZero();
                }
            }
        }
    }

    @Test
    @DisplayName("Cross-tenant insert is rejected by RLS WITH CHECK policy")
    void crossTenantInsertIsBlockedByRlsWithCheck() throws SQLException {
        try (Connection conn = NotificationWorkerTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            NotificationWorkerTestSchema.bindTenant(conn, tenantA);

            assertThatThrownBy(() -> {
                        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.reminder_rule ("
                                + "tenant_id, event, audience, anchor, offset_days, send_at_local_time"
                                + ") VALUES (?, 'TIMESHEET_REMINDER', 'SUBJECT', 'WEEKLY', 0, '09:00:00')")) {
                            // Bound to Tenant A, but attempting to insert row for Tenant B
                            ps.setObject(1, tenantB);
                            ps.executeUpdate();
                        }
                    })
                    .isInstanceOf(SQLException.class);
        }
    }
}
