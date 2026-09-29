package com.infinevo.worker.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.job.service.JobService;
import com.infinevo.core.report.ExportService;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.queue.QueueConsumer;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Queue consumer processing asynchronous report export jobs from the 'report' queue (W-23.2).
 *
 * <p>Enforces idempotency on {@code jobId}: redelivered messages for a job already {@code COMPLETED}
 * or {@code RUNNING} are safely dropped without producing duplicate export files (12-core-contracts.md §5).
 * The job's result payload carries the created {@code documentId}.
 */
@Component
public class AsyncExportConsumer implements QueueConsumer<String> {

    private static final Logger log = LoggerFactory.getLogger(AsyncExportConsumer.class);
    public static final String QUEUE_NAME = "report";

    private final JobService jobService;
    private final ExportService exportService;
    private final ObjectMapper objectMapper;

    public AsyncExportConsumer(JobService jobService, ExportService exportService, ObjectMapper objectMapper) {
        this.jobService = Objects.requireNonNull(jobService, "jobService must not be null");
        this.exportService = Objects.requireNonNull(exportService, "exportService must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public String getQueueName() {
        return QUEUE_NAME;
    }

    @Override
    public void onMessage(QueueMessage<String> message) {
        Objects.requireNonNull(message, "message must not be null");
        String jobId = message.getJobId();
        UUID tenantId = message.getTenantId();

        String correlationId = message.getCorrelationId();
        if (correlationId != null) {
            MDC.put(MdcLoggingContext.CORRELATION_ID_KEY, correlationId);
        }
        MDC.put(MdcLoggingContext.TENANT_ID_KEY, tenantId.toString());

        log.info("Processing async report export job {} for tenant {}", jobId, tenantId);

        TenantContext.set(tenantId);
        try {
            // Atomic claim: if already RUNNING, COMPLETED, or FAILED, drop redelivery (idempotency)
            if (!jobService.claimForRun(jobId)) {
                log.warn(
                        "Job {} could not be claimed (missing, RUNNING, COMPLETED or FAILED) - dropping message.",
                        jobId);
                return;
            }

            jobService.updateProgress(jobId, 25);

            ExportService.ExportRequest request =
                    objectMapper.readValue(message.getPayload(), ExportService.ExportRequest.class);

            jobService.updateProgress(jobId, 50);

            ExportService.ExportResponse response =
                    exportService.exportForJob(request.definitionId(), request.filters());

            jobService.updateProgress(jobId, 100);
            jobService.markCompleted(jobId, response.documentId().toString());

            log.info(
                    "Async report export job {} completed successfully; documentId = {}", jobId, response.documentId());
        } catch (Exception e) {
            log.error("Failed executing async report export job {}: {}", jobId, e.getMessage(), e);
            try {
                jobService.markFailed(jobId, e.getMessage());
            } catch (Exception ex) {
                log.error("Failed marking job {} as failed: {}", jobId, ex.getMessage(), ex);
            }
        } finally {
            MDC.remove(MdcLoggingContext.CORRELATION_ID_KEY);
            MDC.remove(MdcLoggingContext.TENANT_ID_KEY);
            TenantContext.clear();
        }
    }
}
