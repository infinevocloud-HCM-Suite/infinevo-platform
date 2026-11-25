package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * D-144 against real Postgres ({@code V220}): the trigger refuses a CLOSED month only, the lock is
 * raised and removed only the two ways allowed, and the restrictive policies hold even when the
 * service is bypassed.
 */
@SpringBootTest(classes = PayInputTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputLockStageIT extends AbstractIntegrationTest {

    @Autowired
    private PayInputService payInputService;

    private UUID tenant;
    private UUID employee;

    @BeforeEach
    void seed() throws SQLException {
        tenant = PayInputTestSchema.insertTenant("Stage " + UUID.randomUUID());
        employee = PayInputTestSchema.insertEmployee(tenant, "STAGE-" + UUID.randomUUID());
        TenantContext.set(tenant);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    /** One untagged LOP row for {@code period}, as app_user, the service bypassed. */
    private int insertBypassingTheService(String period, String sourceRef) throws SQLException {
        try (Connection conn = PayInputTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayInputTestSchema.bindTenant(conn, tenant);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.pay_input"
                    + " (tenant_id, employee_id, period, kind, quantity, source_module, source_ref)"
                    + " VALUES (?, ?, ?, 'LOP_DAYS', 1, 'test', ?)")) {
                ps.setObject(1, tenant);
                ps.setObject(2, employee);
                ps.setString(3, period);
                ps.setString(4, sourceRef);
                int rows = ps.executeUpdate();
                conn.commit();
                return rows;
            }
        }
    }

    private int executeAsApp(String sql, String period) throws SQLException {
        try (Connection conn = PayInputTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayInputTestSchema.bindTenant(conn, tenant);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenant);
                ps.setString(2, period);
                int rows = ps.executeUpdate();
                conn.commit();
                return rows;
            }
        }
    }

    private String stageOf(String period) throws SQLException {
        long closed = PayInputTestSchema.count(
                "SELECT count(*) FROM core.pay_input_period_lock WHERE tenant_id = ? AND period = '" + period
                        + "' AND run_ref IS NULL AND stage = 'CLOSED'",
                tenant);
        long locked = PayInputTestSchema.count(
                "SELECT count(*) FROM core.pay_input_period_lock WHERE tenant_id = ? AND period = '" + period
                        + "' AND run_ref IS NULL AND stage = 'LOCKED'",
                tenant);
        return closed == 1 ? "CLOSED" : locked == 1 ? "LOCKED" : "NONE";
    }

    @Test
    @DisplayName("Trigger: a LOCKED month takes an untagged insert")
    void lockedMonthTakesAnInsert() throws SQLException {
        payInputService.lock(YearMonth.of(2026, 4));

        assertThat(insertBypassingTheService("2026-04", "locked-1")).isEqualTo(1);
    }

    @Test
    @DisplayName("Trigger: a CLOSED month refuses an untagged insert")
    void closedMonthRefusesAnInsert() {
        payInputService.lock(YearMonth.of(2026, 4), PayInputLockStage.CLOSED);

        assertThatThrownBy(() -> insertBypassingTheService("2026-04", "closed-1"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("Service: recordEntry posts into a LOCKED month and refuses a CLOSED one with the message")
    void recordEntryAgainstBothStages() {
        payInputService.lock(YearMonth.of(2026, 9));
        payInputService.lock(YearMonth.of(2026, 10), PayInputLockStage.CLOSED);

        PayInputResponse locked = payInputService.recordEntry(new PayInputCommand(
                employee, YearMonth.of(2026, 9), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "core", "entry-9"));
        assertThat(locked.postedPeriod()).isEqualTo(YearMonth.of(2026, 9));

        assertThatThrownBy(() -> payInputService.recordEntry(new PayInputCommand(
                        employee, YearMonth.of(2026, 10), PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "core", "e-10")))
                .isInstanceOf(PayInputService.PeriodClosedException.class)
                .hasMessage("October 2026 is closed (run approved). Enter this for November 2026.");

        assertThat(payInputService.posting(YearMonth.of(2026, 10)))
                .isEqualTo(new PayInputPosting(
                        YearMonth.of(2026, 10), YearMonth.of(2026, 11), PayInputPostingState.CLOSED));
    }

    @Test
    @DisplayName("lock CLOSED raises a LOCKED month; lock LOCKED never lowers a CLOSED one")
    void raiseButNeverLower() throws SQLException {
        payInputService.lock(YearMonth.of(2026, 7));
        assertThat(stageOf("2026-07")).isEqualTo("LOCKED");

        payInputService.lock(YearMonth.of(2026, 7), PayInputLockStage.CLOSED);
        assertThat(stageOf("2026-07")).isEqualTo("CLOSED");

        payInputService.lock(YearMonth.of(2026, 7));
        assertThat(stageOf("2026-07")).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("unlock removes a LOCKED lock and refuses a CLOSED one")
    void unlockBothStages() throws SQLException {
        payInputService.lock(YearMonth.of(2026, 5));
        assertThat(payInputService.unlock(YearMonth.of(2026, 5))).isTrue();
        assertThat(stageOf("2026-05")).isEqualTo("NONE");

        payInputService.lock(YearMonth.of(2026, 6), PayInputLockStage.CLOSED);
        assertThatThrownBy(() -> payInputService.unlock(YearMonth.of(2026, 6)))
                .isInstanceOf(PayInputService.PeriodClosedException.class);
        assertThat(stageOf("2026-06")).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("Policies: app_user cannot delete a CLOSED lock nor lower it, even bypassing the service")
    void policiesHoldWithTheServiceBypassed() throws SQLException {
        payInputService.lock(YearMonth.of(2026, 3), PayInputLockStage.CLOSED);

        assertThat(executeAsApp("DELETE FROM core.pay_input_period_lock WHERE tenant_id = ? AND period = ?", "2026-03"))
                .isZero();
        assertThatThrownBy(() -> executeAsApp(
                        "UPDATE core.pay_input_period_lock SET stage = 'LOCKED' WHERE tenant_id = ? AND period = ?",
                        "2026-03"))
                .isInstanceOf(SQLException.class);
        assertThat(stageOf("2026-03")).isEqualTo("CLOSED");
    }
}
