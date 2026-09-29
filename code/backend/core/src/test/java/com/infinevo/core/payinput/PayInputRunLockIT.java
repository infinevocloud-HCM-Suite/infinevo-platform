package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
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
 * W-30.1 §7 — a run lock is a database constraint, not a service check, the same rule
 * {@code PayInputLockIT} proves for a period lock.
 */
@SpringBootTest(classes = PayInputTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputRunLockIT extends AbstractIntegrationTest {

    @Autowired
    private PayInputService payInputService;

    private UUID tenant;
    private UUID employee;

    @BeforeEach
    void seed() throws SQLException {
        tenant = PayInputTestSchema.insertTenant("RunLock " + UUID.randomUUID());
        employee = PayInputTestSchema.insertEmployee(tenant, "RUNLOCK-" + UUID.randomUUID());
        TenantContext.set(tenant);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("An insert tagged to a locked run is refused by the trigger, with the service bypassed")
    void insertForALockedRunIsRefusedByTheTrigger() throws SQLException {
        UUID runRef = UUID.randomUUID();
        payInputService.lockRun(runRef, YearMonth.of(2026, 4));

        try (Connection conn = PayInputTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayInputTestSchema.bindTenant(conn, tenant);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.pay_input"
                    + " (tenant_id, employee_id, period, kind, amount, run_ref, source_module, source_ref)"
                    + " VALUES (?, ?, '2026-04', 'ONE_TIME_PAYOUT', 500, ?, 'test', 'bypass-run-1')")) {
                ps.setObject(1, tenant);
                ps.setObject(2, employee);
                ps.setObject(3, runRef);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("locked");
            }
        }
    }

    @Test
    @DisplayName("An untagged insert for the same calendar period is accepted: a run lock does not lock the period")
    void untaggedInsertForTheSamePeriodIsAccepted() throws SQLException {
        UUID runRef = UUID.randomUUID();
        payInputService.lockRun(runRef, YearMonth.of(2026, 4));

        try (Connection conn = PayInputTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayInputTestSchema.bindTenant(conn, tenant);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.pay_input"
                    + " (tenant_id, employee_id, period, kind, quantity, source_module, source_ref)"
                    + " VALUES (?, ?, '2026-04', 'LOP_DAYS', 1, 'test', 'untagged-during-run-lock')")) {
                ps.setObject(1, tenant);
                ps.setObject(2, employee);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
            conn.commit();
        }
    }

    @Test
    @DisplayName("lockRun twice for one run leaves one row")
    void lockRunTwiceLeavesOneRow() throws SQLException {
        UUID runRef = UUID.randomUUID();
        payInputService.lockRun(runRef, YearMonth.of(2026, 6));
        payInputService.lockRun(runRef, YearMonth.of(2026, 6));

        long count = PayInputTestSchema.count(
                "SELECT count(*) FROM core.pay_input_period_lock WHERE tenant_id = ? AND run_ref = ?", tenant, runRef);
        assertThat(count).isEqualTo(1);
    }
}
