package com.infinevo.worker.listener;

import com.infinevo.core.job.service.JobService;
import com.infinevo.payroll.payrun.PayRunComputationService;
import com.infinevo.payroll.payrun.PayRunJobPayload;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.SupersededPayRunJobException;
import com.infinevo.shared.queue.QueueConsumer;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Queue listener consuming pay run computations from the 'payrun' queue (W-29.4 §3).
 * Enforces idempotency against core.job_status: duplicate deliveries are safely dropped (D-50).
 *
 * <p>The body is one call to {@link PayRunComputationService}: the computation lives in {@code payroll},
 * never here. The outcome decides the job: a {@code COMPUTED} run completes it; a {@code FAILED} run
 * (some employees could not be computed) fails it with the reason, without retry — the rows already
 * say what failed and the officer computes again; a message the run no longer expects, or naming a run
 * the tenant does not hold, fails it without retry. Anything else is released for another delivery.
 */
@Component
public class PayrunQueueListener implements QueueConsumer<String> {

    private static final Logger log = LoggerFactory.getLogger(PayrunQueueListener.class);
    public static final String QUEUE_NAME = "payrun";

    private final JobService jobService;
    private final PayRunComputationService computationService;

    public PayrunQueueListener(JobService jobService, PayRunComputationService computationService) {
        this.jobService = Objects.requireNonNull(jobService, "jobService must not be null");
        this.computationService = Objects.requireNonNull(computationService, "computationService must not be null");
    }

    @Override
    public String getQueueName() {
        return QUEUE_NAME;
    }

    @Override
    public void onMessage(QueueMessage<String> message) {
        Objects.requireNonNull(message, "message must not be null");
        String jobId = message.getJobId();
        var tenantId = message.getTenantId();

        String correlationId = message.getCorrelationId();
        org.slf4j.MDC.put(com.infinevo.shared.logging.MdcLoggingContext.CORRELATION_ID_KEY, correlationId);
        org.slf4j.MDC.put(com.infinevo.shared.logging.MdcLoggingContext.TENANT_ID_KEY, tenantId.toString());

        log.info("Processing payrun job {} for tenant {}", jobId, tenantId);

        TenantContext.set(tenantId);
        try {
            // One UPDATE ... WHERE status = QUEUED. A duplicate delivery, a second replica, or a
            // job already COMPLETED / FAILED all lose the claim and are dropped here (D-50).
            if (!jobService.claimForRun(jobId)) {
                log.warn(
                        "Job {} could not be claimed (missing, RUNNING, COMPLETED or FAILED) - dropping message.",
                        jobId);
                return;
            }

            PayRunResponse run = processPayrunPayload(jobId, message.getPayload());

            if (run.status() == PayRunStatus.COMPUTED) {
                jobService.markCompleted(
                        jobId, "Pay run " + run.id() + " computed: " + run.progressTotal() + " employees");
                log.info("Payrun job {} completed successfully", jobId);
            } else {
                jobService.markFailed(jobId, "Pay run " + run.id() + " " + run.status() + ": " + run.failureReason());
                log.warn("Payrun job {} ended with the run {}: {}", jobId, run.status(), run.failureReason());
            }
        } catch (SupersededPayRunJobException | PayRunNotFoundException | IllegalArgumentException e) {
            // Another delivery would meet the same answer: fail the job now instead of retrying.
            log.warn("Payrun job {} dropped without retry: {}", jobId, e.getMessage());
            jobService.markFailed(jobId, e.getMessage());
        } catch (Exception e) {
            // Retry-then-fail (12-core-contracts §5): hand the job back to QUEUED and rethrow so
            // the loop leaves the message for redelivery. The loop marks it FAILED on the
            // third delivery.
            log.error("Payrun job {} failed, releasing for retry: {}", jobId, e.getMessage(), e);
            jobService.releaseForRetry(jobId, e.getMessage());
            throw new PayrunProcessingException(jobId, e);
        } finally {
            org.slf4j.MDC.remove(com.infinevo.shared.logging.MdcLoggingContext.CORRELATION_ID_KEY);
            org.slf4j.MDC.remove(com.infinevo.shared.logging.MdcLoggingContext.TENANT_ID_KEY);
            TenantContext.clear();
        }
    }

    /** Thrown to the queue loop so the message stays on the queue for another delivery. */
    public static class PayrunProcessingException extends RuntimeException {
        public PayrunProcessingException(String jobId, Throwable cause) {
            super("Payrun job " + jobId + " failed: " + cause.getMessage(), cause);
        }
    }

    /**
     * Computes the attempt the payload names, reporting progress onto the job. A report that moves the
     * percentage touches the job's {@code updated_at}; every report touches the run's — together they
     * keep a healthy run out of the 15-minute stale window.
     */
    protected PayRunResponse processPayrunPayload(String jobId, String payload) {
        PayRunJobPayload job = PayRunJobPayload.fromJson(payload);
        return computationService.compute(
                job.payrunId(),
                job.attempt(),
                job.requestedBy(),
                (done, total) -> jobService.updateProgress(jobId, percentage(done, total)));
    }

    static int percentage(int done, int total) {
        if (total <= 0) {
            return 100;
        }
        return Math.min(100, Math.max(0, done * 100 / total));
    }
}
