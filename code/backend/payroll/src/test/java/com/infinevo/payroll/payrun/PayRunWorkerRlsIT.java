package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.job.service.JobService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.queue.QueueMessage;
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
 * W-29.4 §7 — the worker binds the tenant the message names. A message bound to tenant A whose payload
 * names tenant B's run finds no run: the job is {@code FAILED}, no line is written, B's run is untouched.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunWorkerRlsIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    @Autowired
    private RecordingQueueProducer producer;

    @Autowired
    private JobService jobService;

    private UUID runOfB;
    private ComputeAcceptedResponse acceptedForB;

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
        TenantContext.set(TENANT_B);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.insertWorkedExampleEmployee(
                TENANT_B, "B-01", PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_B));
        runOfB = payRunService.create(JULY).id();
        payRunService.lock(runOfB);
        acceptedForB = payRunService.compute(runOfB);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Bound to A, a payload naming B's run: not found, job FAILED, no line, B's run untouched")
    void workerCannotReachAnotherTenantsRun() throws SQLException {
        String jobId = "rls-" + UUID.randomUUID();
        String payload = new PayRunJobPayload(runOfB, 1, "intruder").toJson();
        TenantContext.set(TENANT_A);
        jobService.createJob(jobId, TENANT_A, PayRunServiceImpl.QUEUE_NAME, payload);
        TenantContext.clear();

        assertThatThrownBy(
                        () -> worker.deliver(QueueMessage.of(jobId, TENANT_A, PayRunServiceImpl.QUEUE_NAME, payload)))
                .isInstanceOf(PayRunNotFoundException.class);

        assertThat(PayRunTestSchema.job(jobId).status()).isEqualTo("FAILED");
        assertThat(PayRunTestSchema.countLines(TENANT_B, runOfB)).isZero();
        TenantContext.set(TENANT_B);
        PayRunResponse untouched = payRunService.get(runOfB);
        assertThat(untouched.status()).isEqualTo(PayRunStatus.COMPUTING);
        assertThat(untouched.progressDone()).isZero();
        assertThat(PayRunTestSchema.job(acceptedForB.jobId()).status()).isEqualTo("QUEUED");
    }
}
