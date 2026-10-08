package com.infinevo.worker.listener;

import com.infinevo.core.employeeimport.EmployeeImportService;
import com.infinevo.core.job.service.JobService;
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
 * Runs bulk employee imports and "Invite all without access" from the {@code import} queue (W-73.7 §4), as
 * {@link PayrunQueueListener} runs pay runs: the body is one call to {@link EmployeeImportService}, which
 * holds every rule; this class claims the job and records how it ended.
 *
 * <p><strong>No retry.</strong> Rows commit one by one, so a second delivery after a half-finished run
 * would find the first rows' employees already there and report them as duplicates. A job that throws is
 * marked {@code FAILED} with the reason; the rows that landed are visible in the employee list, and the
 * import can be run again with the rest. A duplicate delivery loses the claim and is dropped.
 */
@Component
public class EmployeeImportListener implements QueueConsumer<String> {

    private static final Logger log = LoggerFactory.getLogger(EmployeeImportListener.class);

    private final JobService jobService;
    private final EmployeeImportService importService;

    public EmployeeImportListener(JobService jobService, EmployeeImportService importService) {
        this.jobService = Objects.requireNonNull(jobService, "jobService must not be null");
        this.importService = Objects.requireNonNull(importService, "importService must not be null");
    }

    @Override
    public String getQueueName() {
        return EmployeeImportService.QUEUE_NAME;
    }

    @Override
    public void onMessage(QueueMessage<String> message) {
        Objects.requireNonNull(message, "message must not be null");
        String jobId = message.getJobId();
        UUID tenantId = message.getTenantId();

        if (message.getCorrelationId() != null) {
            MDC.put(MdcLoggingContext.CORRELATION_ID_KEY, message.getCorrelationId());
        }
        MDC.put(MdcLoggingContext.TENANT_ID_KEY, tenantId.toString());
        TenantContext.set(tenantId);
        try {
            if (!jobService.claimForRun(jobId)) {
                log.warn(
                        "Job {} could not be claimed (missing, RUNNING, COMPLETED or FAILED) - dropping message.",
                        jobId);
                return;
            }
            log.info("Running employee {} job {} for tenant {}", message.getPayload(), jobId, tenantId);
            String summary = importService.runJob(jobId);
            jobService.markCompleted(jobId, summary);
            log.info("Employee import job {} completed: {}", jobId, summary);
        } catch (Exception e) {
            log.error("Employee import job {} failed: {}", jobId, e.getMessage(), e);
            try {
                jobService.markFailed(
                        jobId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } catch (Exception markFailure) {
                log.error("Failed marking job {} as failed: {}", jobId, markFailure.getMessage(), markFailure);
            }
        } finally {
            MDC.remove(MdcLoggingContext.CORRELATION_ID_KEY);
            MDC.remove(MdcLoggingContext.TENANT_ID_KEY);
            TenantContext.clear();
        }
    }
}
