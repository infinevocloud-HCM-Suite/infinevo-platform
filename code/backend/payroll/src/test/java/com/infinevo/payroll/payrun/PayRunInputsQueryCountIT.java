package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.payinput.PayInputCommand;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.money.Money;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
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
 * W-29.3 §7 — 20 employees with inputs: the ledger is read once per run, not once per employee,
 * and the pro-rata flag lookup is one query per catalogue table (W-55).
 */
@SpringBootTest(classes = PayrollTestApp.class)
@TestPropertySource(
        properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.infinevo.payroll.payrun.PayRunInputsQueryCountIT$StatementCounter")
class PayRunInputsQueryCountIT extends AbstractIntegrationTest {

    private static final int EMPLOYEES = 20;

    public static class StatementCounter implements StatementInspector {
        private static final Pattern LEDGER = Pattern.compile("\\bfrom core\\.pay_input\\s");
        static final AtomicInteger LEDGER_READS = new AtomicInteger();
        static final AtomicInteger EARNING_PRO_RATA_READS = new AtomicInteger();
        static final AtomicInteger BENEFIT_PRO_RATA_READS = new AtomicInteger();

        @Override
        public String inspect(String sql) {
            String s = sql == null ? "" : sql.trim().toLowerCase(Locale.ROOT);
            if (!s.startsWith("select")) {
                return sql;
            }
            if (LEDGER.matcher(s).find()) {
                LEDGER_READS.incrementAndGet();
            }
            int where = s.indexOf(" where ");
            String filter = where < 0 ? "" : s.substring(where);
            if (filter.contains("is_pro_rata")) {
                if (s.contains("from payroll.earning ")) {
                    EARNING_PRO_RATA_READS.incrementAndGet();
                } else if (s.contains("from payroll.benefit ")) {
                    BENEFIT_PRO_RATA_READS.incrementAndGet();
                }
            }
            return sql;
        }

        static void reset() {
            LEDGER_READS.set(0);
            EARNING_PRO_RATA_READS.set(0);
            BENEFIT_PRO_RATA_READS.set(0);
        }
    }

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private PayInputService payInputService;

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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("20 employees with inputs: one ledger read, one pro-rata read per catalogue table")
    void runLevelReadsHappenOnce() throws SQLException {
        YearMonth july = YearMonth.of(2026, 7);
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        PayRunTestSchema.markWorkedExampleProRata(TENANT_A);
        for (int i = 0; i < EMPLOYEES; i++) {
            UUID employee =
                    PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, String.format("I-%02d", i), catalogue);
            payInputService.record(new PayInputCommand(
                    employee, july, PayInputKind.LOP_DAYS, BigDecimal.ONE, null, "hrms", "lop-" + i));
            payInputService.record(new PayInputCommand(
                    employee, july, PayInputKind.OVERTIME, new BigDecimal("2"), Money.of("400"), "hrms", "ot-" + i));
        }
        PayRunResponse run = payRunService.create(july);
        payRunService.lock(run.id());

        StatementCounter.reset();
        PayRunResponse computed = worker.computeNow(run.id());

        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(StatementCounter.LEDGER_READS.get())
                .as("forPeriod, once per run")
                .isEqualTo(1);
        assertThat(StatementCounter.EARNING_PRO_RATA_READS.get())
                .as("earning pro-rata lookup")
                .isEqualTo(1);
        assertThat(StatementCounter.BENEFIT_PRO_RATA_READS.get())
                .as("benefit pro-rata lookup")
                .isEqualTo(1);
        // Every employee got both the LOP and the overtime line: 7 structure lines + LOP + LOP_BENEFIT + OVERTIME.
        assertThat(PayRunTestSchema.countLines(TENANT_A, run.id())).isEqualTo(EMPLOYEES * 10L);
    }
}
