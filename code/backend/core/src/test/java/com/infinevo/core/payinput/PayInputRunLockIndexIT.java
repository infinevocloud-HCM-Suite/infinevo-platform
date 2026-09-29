package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
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
 * W-30.1 §6, §7 — the lock table's unique index was split in two by {@code V060}: one partial
 * index per tenant-period among period locks, one per tenant-run among run locks. A period lock
 * and a run lock for the same calendar period must both be able to insert; that is the whole point
 * of the split, and this class is what proves the migration actually did it, not just the service.
 */
@SpringBootTest(classes = PayInputTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputRunLockIndexIT extends AbstractIntegrationTest {

    @Autowired
    private PayInputService payInputService;

    private UUID tenant;

    @BeforeEach
    void seed() throws SQLException {
        tenant = PayInputTestSchema.insertTenant("RunLockIndex " + UUID.randomUUID());
        TenantContext.set(tenant);
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A period lock and a run lock for the same (tenant, period) both insert")
    void periodLockAndRunLockForTheSamePeriodBothInsert() throws SQLException {
        YearMonth period = YearMonth.of(2026, 8);
        UUID runRef = UUID.randomUUID();

        payInputService.lock(period);
        payInputService.lockRun(runRef, period);

        long periodLocks = PayInputTestSchema.count(
                "SELECT count(*) FROM core.pay_input_period_lock WHERE tenant_id = ? AND period = ? AND run_ref IS NULL",
                tenant,
                period.toString());
        long runLocks = PayInputTestSchema.count(
                "SELECT count(*) FROM core.pay_input_period_lock WHERE tenant_id = ? AND run_ref = ?", tenant, runRef);
        assertThat(periodLocks).isEqualTo(1);
        assertThat(runLocks).isEqualTo(1);
    }

    @Test
    @DisplayName("A second run lock for the same run does not insert a second row")
    void aSecondLockForTheSameRunDoesNotInsert() throws SQLException {
        YearMonth period = YearMonth.of(2026, 9);
        UUID runRef = UUID.randomUUID();

        payInputService.lockRun(runRef, period);
        payInputService.lockRun(runRef, period.plusMonths(1)); // same run, a different period value

        long count = PayInputTestSchema.count(
                "SELECT count(*) FROM core.pay_input_period_lock WHERE tenant_id = ? AND run_ref = ?", tenant, runRef);
        assertThat(count).isEqualTo(1);
    }
}
