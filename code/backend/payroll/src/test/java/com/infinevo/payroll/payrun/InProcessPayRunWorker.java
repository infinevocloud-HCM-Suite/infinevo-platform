package com.infinevo.payroll.payrun;

import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;

/**
 * What the worker does with a {@code payrun} message, in the test's thread (W-29.4 §7): claim the job,
 * compute the attempt the payload names with progress reported onto the job, and complete or fail it.
 * The {@code worker} module's {@code PayrunQueueListener} does the same and is unit-tested there;
 * {@code payroll} cannot depend on {@code worker}, so these integration tests drive the real
 * {@link PayRunComputationService} and {@link JobService} through this stand-in.
 */
public class InProcessPayRunWorker {

    private final PayRunService payRunService;
    private final PayRunComputationService computationService;
    private final JobService jobService;
    private final RecordingQueueProducer producer;

    public InProcessPayRunWorker(
            PayRunService payRunService,
            PayRunComputationService computationService,
            JobService jobService,
            RecordingQueueProducer producer) {
        this.payRunService = Objects.requireNonNull(payRunService);
        this.computationService = Objects.requireNonNull(computationService);
        this.jobService = Objects.requireNonNull(jobService);
        this.producer = Objects.requireNonNull(producer);
    }

    /** {@code POST /compute}, then the one message it sent delivered at once: the run as the worker leaves it. */
    public PayRunResponse computeNow(UUID payrunId) {
        ComputeAcceptedResponse accepted = payRunService.compute(payrunId);
        return deliver(producer.forJob(accepted.jobId()), ProgressReporter.NONE);
    }

    /** Delivers {@code message}; {@code null} when the claim is lost and the message dropped. */
    public PayRunResponse deliver(QueueMessage<String> message) {
        return deliver(message, ProgressReporter.NONE);
    }

    /**
     * Delivers {@code message} with {@code observer} told of each progress report after the job is,
     * so a test can count reports or stop the worker mid-way by throwing from it.
     */
    public PayRunResponse deliver(QueueMessage<String> message, ProgressReporter observer) {
        UUID previous = TenantContext.current().orElse(null);
        String jobId = message.getJobId();
        TenantContext.set(message.getTenantId());
        try {
            if (!jobService.claimForRun(jobId)) {
                return null;
            }
            PayRunJobPayload job = PayRunJobPayload.fromJson(message.getPayload());
            PayRunResponse run;
            try {
                run = computationService.compute(job.payrunId(), job.attempt(), job.requestedBy(), (done, total) -> {
                    jobService.updateProgress(jobId, total <= 0 ? 100 : done * 100 / total);
                    observer.report(done, total);
                });
            } catch (SupersededPayRunJobException | PayRunNotFoundException e) {
                jobService.markFailed(jobId, e.getMessage());
                throw e;
            } catch (RuntimeException e) {
                // As the listener: back to QUEUED for another delivery, which resumes the attempt.
                jobService.releaseForRetry(jobId, e.getMessage());
                throw e;
            }
            if (run.status() == PayRunStatus.COMPUTED) {
                jobService.markCompleted(jobId, "computed");
            } else {
                jobService.markFailed(jobId, String.valueOf(run.failureReason()));
            }
            return run;
        } finally {
            if (previous != null) {
                TenantContext.set(previous);
            } else {
                TenantContext.clear();
            }
        }
    }
}
