package com.infinevo.hrms.hiring;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link JobApplicationService} (W-112).
 */
@Service
@Transactional
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobOpeningRepository jobOpeningRepository;
    private final CandidateRepository candidateRepository;

    @Autowired
    public JobApplicationServiceImpl(
            JobApplicationRepository jobApplicationRepository,
            JobOpeningRepository jobOpeningRepository,
            CandidateRepository candidateRepository) {
        this.jobApplicationRepository =
                Objects.requireNonNull(jobApplicationRepository, "jobApplicationRepository must not be null");
        this.jobOpeningRepository =
                Objects.requireNonNull(jobOpeningRepository, "jobOpeningRepository must not be null");
        this.candidateRepository = Objects.requireNonNull(candidateRepository, "candidateRepository must not be null");
    }

    @Override
    public JobApplicationResponse apply(UUID tenantId, UUID jobOpeningId, UUID candidateId, String createdBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(jobOpeningId, "jobOpeningId must not be null");
        Objects.requireNonNull(candidateId, "candidateId must not be null");

        JobOpening opening = jobOpeningRepository
                .findByIdAndTenantId(jobOpeningId, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job opening not found: " + jobOpeningId));

        Candidate candidate = candidateRepository
                .findByIdAndTenantId(candidateId, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Candidate not found: " + candidateId));

        if (jobApplicationRepository.existsByTenantIdAndJobOpeningIdAndCandidateId(
                tenantId, jobOpeningId, candidateId)) {
            throw new HiringConflictException("Candidate already applied to job opening: " + opening.getOpeningCode());
        }

        JobApplication app = new JobApplication();
        app.setTenantId(tenantId);
        app.setJobOpeningId(jobOpeningId);
        app.setCandidateId(candidateId);
        app.setStage(JobApplicationStage.APPLIED);
        app.setAppliedAt(Instant.now());
        app.setCreatedBy(createdBy != null ? createdBy : "system");
        app.setUpdatedBy(app.getCreatedBy());

        JobApplication saved = jobApplicationRepository.save(app);
        return toResponse(saved, opening, candidate);
    }

    @Override
    @Transactional(readOnly = true)
    public JobApplicationResponse getApplication(UUID tenantId, UUID id) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job application not found: " + id));

        JobOpening opening = jobOpeningRepository
                .findByIdAndTenantId(app.getJobOpeningId(), tenantId)
                .orElse(null);
        Candidate candidate = candidateRepository
                .findByIdAndTenantId(app.getCandidateId(), tenantId)
                .orElse(null);
        return toResponse(app, opening, candidate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> listApplicationsByOpening(UUID tenantId, UUID jobOpeningId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(jobOpeningId, "jobOpeningId must not be null");

        JobOpening opening =
                jobOpeningRepository.findByIdAndTenantId(jobOpeningId, tenantId).orElse(null);
        List<JobApplication> apps =
                jobApplicationRepository.findAllByTenantIdAndJobOpeningIdOrderByAppliedAtDesc(tenantId, jobOpeningId);

        return apps.stream()
                .map(app -> {
                    Candidate c = candidateRepository
                            .findByIdAndTenantId(app.getCandidateId(), tenantId)
                            .orElse(null);
                    return toResponse(app, opening, c);
                })
                .toList();
    }

    @Override
    public JobApplicationResponse changeStage(
            UUID tenantId, UUID id, JobApplicationStage stage, String rejectionReason, String updatedBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(stage, "stage must not be null");

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job application not found: " + id));

        app.setStage(stage);
        if (stage == JobApplicationStage.REJECTED) {
            app.setRejectionReason(rejectionReason);
        }
        app.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        JobApplication saved = jobApplicationRepository.save(app);

        JobOpening opening = jobOpeningRepository
                .findByIdAndTenantId(app.getJobOpeningId(), tenantId)
                .orElse(null);
        Candidate candidate = candidateRepository
                .findByIdAndTenantId(app.getCandidateId(), tenantId)
                .orElse(null);
        return toResponse(saved, opening, candidate);
    }

    private JobApplicationResponse toResponse(JobApplication a, JobOpening o, Candidate c) {
        String openingTitle = o != null ? o.getTitle() : null;
        String openingCode = o != null ? o.getOpeningCode() : null;
        String candName = c != null ? c.getFirstName() + " " + c.getLastName() : null;
        String candEmail = c != null ? c.getEmail() : null;
        String candPhone = c != null ? c.getPhone() : null;
        UUID resumeId = c != null ? c.getResumeDocumentId() : null;

        return new JobApplicationResponse(
                a.getId(),
                a.getJobOpeningId(),
                openingTitle,
                openingCode,
                a.getCandidateId(),
                candName,
                candEmail,
                candPhone,
                resumeId,
                a.getStage(),
                a.getRejectionReason(),
                a.getAppliedAt(),
                a.getUpdatedAt());
    }
}
