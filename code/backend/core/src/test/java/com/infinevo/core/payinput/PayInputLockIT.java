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
 * W-19 §7 — the lock is a database constraint, not a service check.
 *
 * <p>{@link #insertAfterLockIsRefusedByTheTrigger()} goes around {@link PayInputService} on
 * purpose: a lock only the service honours is not a lock (spec §6).
 */
@SpringBootTest(classes = PayInputTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputLockIT extends AbstractIntegrationTest {

    @Autowired
    private PayInputService payInputService;

    private UUID tenant;
    private UUID employee;

    @BeforeEach
    void seed() throws SQLException {
        tenant = PayInputTestSchema.insertTenant("Lock " + UUID.randomUUID());
        employee = PayInputTestSchema.insertEmployee(tenant, "LOCK-" + UUID.randomUUID());
        TenantContext.set(tenant);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("An insert after lock is refused by the trigger, with the service bypassed")
    void insertAfterLockIsRefusedByTheTrigger() throws SQLException {
        payInputService.lock(YearMonth.of(2026, 4));

        try (Connection conn = PayInputTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayInputTestSchema.bindTenant(conn, tenant);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.pay_input"
                    + " (tenant_id, employee_id, period, kind, quantity, source_module, source_ref)"
                    + " VALUES (?, ?, '2026-04', 'LOP_DAYS', 1, 'test', 'bypass-1')")) {
                ps.setObject(1, tenant);
                ps.setObject(2, employee);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("locked");
            }
        }
    }

    @Test
    @DisplayName("An insert for a different, unlocked period succeeds")
    void insertForAnUnlockedPeriodSucceeds() throws SQLException {
        payInputService.lock(YearMonth.of(2026, 4));

        try (Connection conn = PayInputTestSchema.appConnection()) {
            conn.setAutoCommit(false);
            PayInputTestSchema.bindTenant(conn, tenant);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO core.pay_input"
                    + " (tenant_id, employee_id, period, kind, quantity, source_module, source_ref)"
                    + " VALUES (?, ?, '2026-05', 'LOP_DAYS', 1, 'test', 'unlocked-1')")) {
                ps.setObject(1, tenant);
                ps.setObject(2, employee);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
            conn.commit();
        }
    }

    @Test
    @DisplayName("lock twice for one period leaves one row")
    void lockTwiceLeavesOneRow() throws SQLException {
        payInputService.lock(YearMonth.of(2026, 6));
        payInputService.lock(YearMonth.of(2026, 6));

        long count = PayInputTestSchema.count(
                "SELECT count(*) FROM core.pay_input_period_lock WHERE tenant_id = ? AND period = '2026-06'", tenant);
        assertThat(count).isEqualTo(1);
    }
}
