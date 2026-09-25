package com.infinevo.core.job.serviceimpl;

import com.infinevo.core.job.JobState;
import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.entity.JobStatus;
import com.infinevo.core.job.repository.JobStatusRepository;
import com.infinevo.core.job.service.JobService;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobServiceImpl implements JobService {

    private final JobStatusRepository jobStatusRepository;

    public JobServiceImpl(JobStatusRepository jobStatusRepository) {
        this.jobStatusRepository = Objects.requireNonNull(jobStatusRepository, "jobStatusRepository must not be null");
    }

    @Override
    @Transactional
    public JobStatus createJob(String jobId, UUID tenantId, String queueName, String initialPayload) {
        JobStatus job = new JobStatus(jobId, tenantId, queueName, JobState.QUEUED);
        job.setResultPayload(initialPayload);
        job.setProgressPercentage(0);
        return jobStatusRepository.save(job);
    }

    @Override
    @Transactional
    public void markRunning(String jobId) {
        jobStatusRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() != JobState.COMPLETED && job.getStatus() != JobState.FAILED) {
                job.setStatus(JobState.RUNNING);
                jobStatusRepository.save(job);
            }
        });
    }

    @Override
    @Transactional
    public boolean claimForRun(String jobId) {
        return jobStatusRepository.transition(jobId, JobState.QUEUED, JobState.RUNNING, Instant.now()) == 1;
    }

    @Override
    @Transactional
    public void releaseForRetry(String jobId, String errorMessage) {
        jobStatusRepository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() == JobState.RUNNING) {
                job.setStatus(JobState.QUEUED);
                job.setErrorMessage(errorMessage);
                jobStatusRepository.save(job);
            }
        });
    }

    @Override
    @Transactional
    public void updateProgress(String jobId, int progressPercentage) {
        jobStatusRepository.findById(jobId).ifPresent(job -> {
            job.setProgressPercentage(progressPercentage);
            jobStatusRepository.save(job);
        });
    }

    @Override
    @Transactional
    public void markCompleted(String jobId, String resultPayload) {
        jobStatusRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobState.COMPLETED);
            job.setProgressPercentage(100);
            job.setResultPayload(resultPayload);
            jobStatusRepository.save(job);
        });
    }

    @Override
    @Transactional
    public void markFailed(String jobId, String errorMessage) {
        jobStatusRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobState.FAILED);
            // Keep the last attempt's error next to the reason the loop gave up.
            String previous = job.getErrorMessage();
            job.setErrorMessage(
                    previous == null || previous.isBlank()
                            ? errorMessage
                            : errorMessage + " (last attempt: " + previous + ")");
            jobStatusRepository.save(job);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JobStatusResponseDTO> getJobStatus(String jobId, UUID tenantId) {
        return jobStatusRepository
                .findByJobIdAndTenantId(jobId, tenantId)
                .map(job -> new JobStatusResponseDTO(
                        job.getJobId(),
                        job.getQueueName(),
                        job.getStatus(),
                        job.getProgressPercentage(),
                        job.getErrorMessage(),
                        job.getCreatedAt(),
                        job.getUpdatedAt()));
    }
}
