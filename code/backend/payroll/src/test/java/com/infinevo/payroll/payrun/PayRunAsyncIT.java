package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-29.4 §7, the acceptance test: 100 included employees. {@code POST /compute} returns at once with the
 * run {@code COMPUTING} and one message queued; the worker computes it to {@code COMPUTED} with
 * {@code progress_done = 100}, the job {@code COMPLETED} at 100, at most eleven progress reports, and the
 * §8 worked example's net on every row.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunAsyncIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final int EMPLOYEES = 100;

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private RecordingQueueProducer producer;

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
        producer.clear();
        TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        for (int i = 1; i <= EMPLOYEES; i++) {
            PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, String.format("A-%03d", i), catalogue);
        }
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("100 employees: 202 at once, the worker reaches COMPUTED at 100/100, the job COMPLETED at 100")
    void computesOnTheWorkerWithProgress() throws SQLException {
        PayRunResponse created = payRunService.create(JULY);
        payRunService.lock(created.id());
        assertThat(created.includedCount()).isEqualTo(EMPLOYEES);

        long started = System.nanoTime();
        ComputeAcceptedResponse accepted = payRunService.compute(created.id());
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(took).isLessThan(Duration.ofSeconds(1));
        assertThat(accepted.status()).isEqualTo(PayRunStatus.COMPUTING);
        assertThat(accepted.computeAttempt()).isEqualTo(1);
        PayRunResponse queued = payRunService.get(created.id());
        assertThat(queued.status()).isEqualTo(PayRunStatus.COMPUTING);
        assertThat(queued.jobId()).isEqualTo(accepted.jobId());
        assertThat(queued.progressDone()).isZero();
        assertThat(queued.progressTotal()).isEqualTo(EMPLOYEES);
        assertThat(PayRunTestSchema.countLines(TENANT_A, created.id())).isZero();
        assertThat(PayRunTestSchema.job(accepted.jobId()).status()).isEqualTo("QUEUED");
        List<QueueMessage<String>> sent = producer.sent();
        assertThat(sent).hasSize(1);

        List<Integer> reports = new ArrayList<>();
        PayRunResponse computed = worker.deliver(sent.get(0), (done, total) -> reports.add(done));

        assertThat(computed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(computed.progressDone()).isEqualTo(EMPLOYEES);
        assertThat(computed.progressTotal()).isEqualTo(EMPLOYEES);
        assertThat(computed.totalNetPay()).isEqualByComparingTo("4700000.00");
        assertThat(reports).hasSizeLessThanOrEqualTo(11).containsExactly(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        assertThat(PayRunTestSchema.rowsAtAttempt(TENANT_A, created.id(), 1)).isEqualTo(EMPLOYEES);
        assertThat(PayRunTestSchema.countLines(TENANT_A, created.id())).isEqualTo(7L * EMPLOYEES);
        PayRunTestSchema.JobRow job = PayRunTestSchema.job(accepted.jobId());
        assertThat(job.status()).isEqualTo("COMPLETED");
        assertThat(job.progress()).isEqualTo(100);
    }
}
