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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * W-29.2 §7 — computing 20 employees costs a bounded number of statements per employee: one salary
 * version read each (with its component lookups), and one batched INSERT of each employee's lines
 * rather than one per line ({@code QueryCountIT}'s style, W-55). The counter sees statements as
 * Hibernate prepares them, so a JDBC batch counts once.
 */
@SpringBootTest(classes = PayrollTestApp.class)
@TestPropertySource(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.infinevo.payroll.payrun.PayRunComputeQueryCountIT$StatementCounter")
class PayRunComputeQueryCountIT extends AbstractIntegrationTest {

    private static final int EMPLOYEES = 20;
    private static final int LINES_PER_EMPLOYEE = 7;

    /**
     * Measured at 25 for the worked-example structure: about 14 are W-26.2's {@code versionInForce}
     * (the version, its three line lists, FBP declarations, one catalogue lookup per component and the
     * statutory lines), one is the contributor's catalogue read, the rest are the row read and write.
     * The bound is per employee and independent of how many employees the run holds.
     */
    private static final int SELECTS_PER_EMPLOYEE = 30;

    public static class StatementCounter implements StatementInspector {
        static final AtomicInteger SELECTS = new AtomicInteger();
        static final AtomicInteger LINE_INSERTS = new AtomicInteger();

        @Override
        public String inspect(String sql) {
            String s = sql == null ? "" : sql.trim().toLowerCase(Locale.ROOT);
            if (s.startsWith("select")) {
                SELECTS.incrementAndGet();
            } else if (s.startsWith("insert into payroll.employee_payrun_line")) {
                LINE_INSERTS.incrementAndGet();
            }
            return sql;
        }

        static void reset() {
            SELECTS.set(0);
            LINE_INSERTS.set(0);
        }
    }

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

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
        for (int i = 0; i < EMPLOYEES; i++) {
            PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, String.format("Q-%02d", i), catalogue);
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("20 employees: one batched line INSERT each, and a constant number of SELECTs per employee")
    void computeIsBoundedPerEmployee() throws SQLException {
        PayRunResponse run = payRunService.create(YearMonth.of(2026, 7));
        payRunService.lock(run.id());

        StatementCounter.reset();
        PayRunResponse computed = worker.computeNow(run.id());
        int selects = StatementCounter.SELECTS.get();
        int lineInserts = StatementCounter.LINE_INSERTS.get();

        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(PayRunTestSchema.countLines(TENANT_A, run.id())).isEqualTo((long) EMPLOYEES * LINES_PER_EMPLOYEE);
        assertThat(lineInserts)
                .as("one batched INSERT per employee, not one per line")
                .isEqualTo(EMPLOYEES);
        assertThat(selects)
                .as("SELECTs are bounded per employee")
                .isLessThanOrEqualTo(EMPLOYEES * SELECTS_PER_EMPLOYEE);
    }
}
