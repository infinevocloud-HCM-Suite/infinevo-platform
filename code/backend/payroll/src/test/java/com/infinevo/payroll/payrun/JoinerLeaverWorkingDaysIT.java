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
 * W-18.2 — the W-29 merge's outstanding defect, end to end. July 2026, Monday to Friday from the pay
 * schedule, {@code ORG_DAYS} counting working days: divisor 23. On the worked example's 42,500 pro-rata
 * base, a joiner on the 16th is outside for 11 working days and keeps 12 of 23 — 22,173.91 — where
 * calendar days (15 of them) left 14,782.61. A leaver on the 10th is outside for the 15 working days after.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class JoinerLeaverWorkingDaysIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayFigureExplanationService explanationService;

    private UUID joiner;
    private UUID leaver;

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
        PayRunTestSchema.setPolicy(TENANT_A, "ORG_DAYS", false, true, "HALF_UP_2");
        TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        PayRunTestSchema.markWorkedExampleProRata(TENANT_A);
        joiner = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "J-01", catalogue);
        PayRunTestSchema.execute("UPDATE core.employee SET date_of_joining = DATE '2026-07-16' WHERE id = ?", joiner);
        leaver = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "J-02", catalogue);
        PayRunTestSchema.execute(
                "UPDATE core.employee SET status = 'TERMINATED', termination_date = DATE '2026-07-10' WHERE id = ?",
                leaver);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Joiner on 16 July: 11 working days outside, 42,500 × 11 ÷ 23 = 20,326.09 off — 22,173.91 kept")
    void joinerIsChargedForWorkingDaysOnly() {
        PayRunResponse run = computeJuly();

        PayFigureExplanationResponse explained = explanationService.explain(run.id(), joiner);

        assertThat(explained.unpaidDays()).isEqualByComparingTo("11.00");
        assertThat(explained.paidDays()).isEqualByComparingTo("12.00");
        assertThat(explained.payDivisor()).isEqualByComparingTo("23.00");
        assertThat(explained.lopAmount()).isEqualByComparingTo("20326.09");
        assertThat(explained.lopReversalAmount())
                .as("nothing given back this month")
                .isEqualByComparingTo("0");
        assertThat(new java.math.BigDecimal("42500").subtract(explained.lopAmount()))
                .isEqualByComparingTo("22173.91");
    }

    @Test
    @DisplayName("Leaver on 10 July: 15 working days after, 42,500 × 15 ÷ 23 = 27,717.39 off")
    void leaverIsChargedForWorkingDaysOnly() {
        PayRunResponse run = computeJuly();

        PayFigureExplanationResponse explained = explanationService.explain(run.id(), leaver);

        assertThat(explained.unpaidDays()).isEqualByComparingTo("15.00");
        assertThat(explained.paidDays()).isEqualByComparingTo("8.00");
        assertThat(explained.lopAmount()).isEqualByComparingTo("27717.39");
    }

    private PayRunResponse computeJuly() {
        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        PayRunResponse computed = worker.computeNow(run.id());
        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        TenantContext.set(TENANT_A);
        return computed;
    }
}
