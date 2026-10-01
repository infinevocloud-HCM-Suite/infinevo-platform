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
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
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
 * W-29.4 §7 — the same message delivered twice computes once: one set of lines, one {@code computed_at}
 * per row, the job untouched by the second delivery. A second press while the first is queued is
 * refused and sends nothing.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class PayRunDuplicateMessageIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

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
        for (int i = 1; i <= 3; i++) {
            PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "D-0" + i, catalogue);
        }
        runId = payRunService.create(JULY).id();
        payRunService.lock(runId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Delivered twice: the second delivery loses the claim; one set of lines, rows written once")
    void duplicateDeliveryComputesOnce() throws SQLException {
        ComputeAcceptedResponse accepted = payRunService.compute(runId);
        QueueMessage<String> message = producer.forJob(accepted.jobId());

        PayRunResponse first = worker.deliver(message);
        assertThat(first.status()).isEqualTo(PayRunStatus.COMPUTED);
        long lines = PayRunTestSchema.countLines(TENANT_A, runId);
        Map<UUID, Instant> computedAt = PayRunTestSchema.computedAtByRow(TENANT_A, runId);
        PayRunTestSchema.JobRow job = PayRunTestSchema.job(accepted.jobId());

        PayRunResponse second = worker.deliver(message);

        assertThat(second).as("the claim is lost, the message dropped").isNull();
        assertThat(lines).isEqualTo(21);
        assertThat(PayRunTestSchema.countLines(TENANT_A, runId)).isEqualTo(lines);
        assertThat(PayRunTestSchema.computedAtByRow(TENANT_A, runId)).isEqualTo(computedAt);
        assertThat(PayRunTestSchema.job(accepted.jobId())).isEqualTo(job);
        assertThat(payRunService.get(runId).status()).isEqualTo(PayRunStatus.COMPUTED);
    }

    @Test
    @DisplayName("A second press while the first is queued is 409 and queues nothing")
    void secondPressWhileQueuedIsRefused() {
        payRunService.compute(runId);

        assertThatThrownBy(() -> payRunService.compute(runId)).isInstanceOf(PayRunComputeInProgressException.class);

        assertThat(producer.sent()).hasSize(1);
        assertThat(payRunService.get(runId).computeAttempt()).isEqualTo(1);
    }
}
