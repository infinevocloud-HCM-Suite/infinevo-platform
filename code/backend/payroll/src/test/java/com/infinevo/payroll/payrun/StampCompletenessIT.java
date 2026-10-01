package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-18.2 §7 — the test that makes "the stamp is not optional" real: after a pay run, no computed
 * {@code employee_payrun} row has a null in any of the five stamp columns, {@code lop_policy_id}
 * included, and every row names the tenant's policy version, its basis, divisor and rounding. Read
 * straight from the table, so a service that forgot to set a column cannot hide it.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class StampCompletenessIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayRunTestSchema.clean();
        TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        for (int i = 1; i <= 5; i++) {
            PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "S-0" + i, catalogue);
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "After a run: zero null stamp columns on computed rows; each names the policy, basis, divisor, rounding")
    void everyComputedRowIsFullyStamped() throws SQLException {
        UUID policy = PayRunTestSchema.policyId(TENANT_A);
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());

        PayRunResponse computed = worker.computeNow(run.id());

        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        List<PayRunTestSchema.StampRow> rows = PayRunTestSchema.stamps(TENANT_A, run.id());
        assertThat(rows).hasSize(5);
        assertThat(rows)
                .filteredOn(row -> row.computationError() == null)
                .hasSize(5)
                .allSatisfy(row -> {
                    assertThat(row.fullyStamped()).as("no null stamp column").isTrue();
                    assertThat(row.lopPolicyId()).isEqualTo(policy);
                    // PayRunTestSchema.clean() puts the tenant on ACTUAL_DAYS, weekends and holidays payable.
                    assertThat(row.workingDayBasis()).isEqualTo("ACTUAL_DAYS");
                    assertThat(row.payDivisor()).isEqualByComparingTo("31.00");
                    assertThat(row.payableDays()).isEqualByComparingTo("31.00");
                    assertThat(row.lopRounding()).isEqualTo("HALF_UP_2");
                });
    }
}
