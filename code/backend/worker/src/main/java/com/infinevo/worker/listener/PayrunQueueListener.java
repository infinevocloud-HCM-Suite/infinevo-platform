package com.infinevo.worker.listener;

import com.infinevo.core.job.JobState;
import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.queue.QueueConsumer;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Queue listener consuming background pay run processing jobs from 'payrun' queue.
 * Enforces idempotency against core.job_status: duplicate deliveries are safely dropped (D-50).
 */
@Component
public class PayrunQueueListener implements QueueConsumer<String> {

    private static final Logger log = LoggerFactory.getLogger(PayrunQueueListener.class);
    public static final String QUEUE_NAME = "payrun";

    private final JobService jobService;

    public PayrunQueueListener(JobService jobService) {
        this.jobService = Objects.requireNonNull(jobService, "jobService must not be null");
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
            Optional<JobStatusResponseDTO> currentStatus = jobService.getJobStatus(jobId, tenantId);
            if (currentStatus.isPresent()) {
                JobState state = currentStatus.get().status();
                if (state == JobState.COMPLETED || state == JobState.RUNNING) {
                    log.warn("Job {} is already {} - dropping duplicate message.", jobId, state);
                    return;
                }
            }

            jobService.markRunning(jobId);
            jobService.updateProgress(jobId, 25);

            // Execute payload processing
            processPayrunPayload(message.getPayload());

            jobService.updateProgress(jobId, 75);
            jobService.markCompleted(jobId, "Pay run completed successfully");
            log.info("Payrun job {} completed successfully", jobId);
        } catch (Exception e) {
            log.error("Failed to process payrun job {}: {}", jobId, e.getMessage(), e);
            jobService.markFailed(jobId, e.getMessage());
        } finally {
            org.slf4j.MDC.remove(com.infinevo.shared.logging.MdcLoggingContext.CORRELATION_ID_KEY);
            org.slf4j.MDC.remove(com.infinevo.shared.logging.MdcLoggingContext.TENANT_ID_KEY);
            TenantContext.clear();
        }
    }

    protected void processPayrunPayload(String payload) {
        // Business execution hook for pay run calculation
        log.debug("Processed payrun payload (length: {} chars)", payload != null ? payload.length() : 0);
    }
}
