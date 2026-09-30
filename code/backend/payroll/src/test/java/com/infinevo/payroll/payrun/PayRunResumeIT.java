package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.io.Serial;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-29.4 §7 — resume. The worker dies after 40 of 100 rows: the run is {@code COMPUTING} with 40 rows at
 * attempt 1 and the job {@code RUNNING}. Within 15 minutes a new press is refused; once the job is 16
 * minutes old it starts attempt 2, which computes the other 60 and ends {@code COMPUTED} with all 100
 * rows at attempt 2. The dead worker's message, delivered again, is dropped.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunResumeIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final int EMPLOYEES = 100;

    /** Stands for the worker process dying: not a RuntimeException, so nothing on the way out handles it. */
    static final class WorkerKilled extends Error {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private RecordingQueueProducer producer;

    private UUID runId;

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
            PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, String.format("R-%03d", i), catalogue);
        }
        runId = payRunService.create(JULY).id();
        payRunService.lock(runId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Killed after 40: COMPUTING, 40 at attempt 1; stale press starts attempt 2; 60 more; COMPUTED")
    void abandonedRunResumesWhereItStopped() throws SQLException {
        ComputeAcceptedResponse first = payRunService.compute(runId);
        QueueMessage<String> firstMessage = producer.forJob(first.jobId());

        try {
            worker.deliver(firstMessage, (done, total) -> {
                if (done == 40) {
                    throw new WorkerKilled();
                }
            });
        } catch (WorkerKilled expected) {
            // The worker is gone: nothing released the job or finished the run.
        }
        TenantContext.set(TENANT_A);

        PayRunResponse abandoned = payRunService.get(runId);
        assertThat(abandoned.status()).isEqualTo(PayRunStatus.COMPUTING);
        assertThat(abandoned.progressDone()).isEqualTo(40);
        assertThat(PayRunTestSchema.rowsAtAttempt(TENANT_A, runId, 1)).isEqualTo(40);
        assertThat(PayRunTestSchema.job(first.jobId()).status()).isEqualTo("RUNNING");

        assertThatThrownBy(() -> payRunService.compute(runId))
                .as("within the stale window")
                .isInstanceOf(PayRunComputeInProgressException.class);

        PayRunTestSchema.backdateJob(first.jobId(), 16);
        ComputeAcceptedResponse second = payRunService.compute(runId);
        assertThat(second.computeAttempt()).isEqualTo(2);
        assertThat(payRunService.get(runId).progressDone())
                .as("carried forward")
                .isEqualTo(40);
        assertThat(PayRunTestSchema.rowsAtAttempt(TENANT_A, runId, 2)).isEqualTo(40);

        AtomicInteger computedThisTime = new AtomicInteger();
        PayRunResponse resumed =
                worker.deliver(producer.forJob(second.jobId()), (done, total) -> computedThisTime.set(done - 40));

        assertThat(resumed.status()).isEqualTo(PayRunStatus.COMPUTED);
        assertThat(computedThisTime.get()).isEqualTo(60);
        assertThat(resumed.progressDone()).isEqualTo(EMPLOYEES);
        assertThat(resumed.totalNetPay()).isEqualByComparingTo("4700000.00");
        assertThat(PayRunTestSchema.rowsAtAttempt(TENANT_A, runId, 2)).isEqualTo(EMPLOYEES);
        assertThat(PayRunTestSchema.countLines(TENANT_A, runId)).isEqualTo(7L * EMPLOYEES);
        assertThat(PayRunTestSchema.job(second.jobId()).status()).isEqualTo("COMPLETED");

        // The dead worker's message comes back: its job is still RUNNING, so the claim is lost.
        assertThat(worker.deliver(firstMessage)).isNull();
        assertThat(PayRunTestSchema.countLines(TENANT_A, runId)).isEqualTo(7L * EMPLOYEES);
    }
}
