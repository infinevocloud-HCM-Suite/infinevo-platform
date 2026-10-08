package com.infinevo.payroll.scheduled;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.EmployeePayRunLineResponse;
import com.infinevo.payroll.payrun.EmployeePayRunLinesResponse;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
 * W-73.6 §7 — {@code PayRunScheduledLineIT}: a bonus scheduled for next month appears on next
 * month's run as a taxable earning line through the unchanged {@code PayInputLineContributor}, and
 * on no other run.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunScheduledLineIT extends AbstractIntegrationTest {

    private static final YearMonth NEXT_MONTH = YearMonth.now(ZoneOffset.UTC).plusMonths(1);

    @Autowired
    private ScheduledEarningService scheduledEarnings;

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    private UUID first;
    private UUID second;
    private UUID bonus;

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
        first = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-01", catalogue);
        second = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "W-02", catalogue);
        bonus = PayRunTestSchema.insertEarningComponent(TENANT_A, "SCHED_BONUS", "Bonus", true, true, false);
        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps =
                        conn.prepareStatement("UPDATE payroll.earning SET is_scheduled_earning = true WHERE id = ?")) {
            ps.setObject(1, bonus);
            ps.executeUpdate();
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Bonus 30,000 scheduled for next month: a taxable ONE_TIME_PAYOUT line on that run, for that employee only")
    void scheduledBonusIsALineOnTheRun() {
        scheduledEarnings.create(
                first, new ScheduledEarningRequest(bonus, new BigDecimal("30000"), NEXT_MONTH.toString(), 1, "Bonus"));

        PayRunResponse run = payRunService.create(NEXT_MONTH);
        payRunService.lock(run.id());
        PayRunResponse computed = worker.computeNow(run.id());
        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);

        EmployeePayRunLinesResponse lines = payRunService.lines(run.id(), first);
        assertThat(lines.computationError()).isNull();
        assertThat(lines.lines())
                .extracting(
                        EmployeePayRunLineResponse::componentCode,
                        EmployeePayRunLineResponse::lineKind,
                        EmployeePayRunLineResponse::amount,
                        EmployeePayRunLineResponse::taxable)
                .contains(tuple("ONE_TIME_PAYOUT", LineKind.EARNING, new BigDecimal("30000.0000"), true));

        // The worked example's gross is 45,000; the bonus takes this employee to 75,000 and the other stays.
        assertThat(lines.lines().stream()
                        .filter(l -> l.lineKind() == LineKind.EARNING)
                        .map(EmployeePayRunLineResponse::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("75000.0000");
        assertThat(payRunService.lines(run.id(), second).lines())
                .extracting(EmployeePayRunLineResponse::componentCode)
                .doesNotContain("ONE_TIME_PAYOUT");
        assertThat(computed.totalGross()).isEqualByComparingTo("120000.0000");
    }
}
