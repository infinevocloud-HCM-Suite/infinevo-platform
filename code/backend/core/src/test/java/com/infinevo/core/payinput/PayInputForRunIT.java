package com.infinevo.core.payinput;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.math.BigDecimal;
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
 * W-30.1 §7, §9 — the split between {@code forRun} and {@code forPeriod} is the ticket's one
 * expensive risk: if {@code forPeriod} kept returning tagged rows, a bonus paid off-cycle would be
 * paid again by the regular run at month end.
 */
@SpringBootTest(classes = PayInputTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            PayInputTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class PayInputForRunIT extends AbstractIntegrationTest {

    @Autowired
    private PayInputService payInputService;

    private UUID tenant;
    private UUID employeeA;
    private UUID employeeB;
    private UUID employeeC;
    private UUID runA;
    private UUID runB;
    private static final YearMonth PERIOD = YearMonth.of(2026, 4);

    @BeforeEach
    void seed() throws SQLException {
        tenant = PayInputTestSchema.insertTenant("ForRun " + UUID.randomUUID());
        employeeA = PayInputTestSchema.insertEmployee(tenant, "FORRUN-A-" + UUID.randomUUID());
        employeeB = PayInputTestSchema.insertEmployee(tenant, "FORRUN-B-" + UUID.randomUUID());
        employeeC = PayInputTestSchema.insertEmployee(tenant, "FORRUN-C-" + UUID.randomUUID());
        runA = UUID.randomUUID();
        runB = UUID.randomUUID();
        TenantContext.set(tenant);

        // A and B are tagged to two different off-cycle runs; C is untagged, the regular run.
        payInputService.record(new PayInputCommand(
                employeeA, PERIOD, PayInputKind.ONE_TIME_PAYOUT, null, Money.of("500"), "payroll", "run-a-1", runA));
        payInputService.record(new PayInputCommand(
                employeeB, PERIOD, PayInputKind.ONE_TIME_PAYOUT, null, Money.of("750"), "payroll", "run-b-1", runB));
        payInputService.record(new PayInputCommand(
                employeeC, PERIOD, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "regular-1"));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("forRun(A) returns only A's tagged rows, in one statement, with A's own totals")
    void forRunReturnsOnlyThatRunsRows() {
        PayInputRunResponse response = payInputService.forRun(runA);

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).employeeId()).isEqualTo(employeeA);
        assertThat(response.rows().get(0).runRef()).isEqualTo(runA);
        assertThat(response.amountTotalsByEmployeeAndKind().get(employeeA).get(PayInputKind.ONE_TIME_PAYOUT))
                .isEqualByComparingTo("500");
        assertThat(response.amountTotalsByEmployeeAndKind()).doesNotContainKey(employeeB);
    }

    @Test
    @DisplayName("forPeriod returns only the untagged rows: neither off-cycle run's bonus is paid again")
    void forPeriodReturnsOnlyUntaggedRows() {
        PayInputListResponse response = payInputService.forPeriod(PERIOD);

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).employeeId()).isEqualTo(employeeC);
        assertThat(response.rows()).allSatisfy(row -> assertThat(row.runRef()).isNull());
    }

    @Test
    @DisplayName("forEmployee for a tagged employee's period returns nothing: forRun is the only read for a tagged row")
    void forEmployeeExcludesTaggedRows() {
        PayInputListResponse response = payInputService.forEmployee(employeeA, PERIOD);

        assertThat(response.rows()).isEmpty();
    }
}
