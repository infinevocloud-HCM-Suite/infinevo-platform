package com.infinevo.hrms.hiring;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link InterviewService} (W-112).
 */
@Service
@Transactional
public class InterviewServiceImpl implements InterviewService {

    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobOpeningRepository jobOpeningRepository;
    private final CandidateRepository candidateRepository;
    private final EmployeeService employeeService;

    @Autowired
    public InterviewServiceImpl(
            InterviewRepository interviewRepository,
            InterviewFeedbackRepository interviewFeedbackRepository,
            JobApplicationRepository jobApplicationRepository,
            JobOpeningRepository jobOpeningRepository,
            CandidateRepository candidateRepository,
            @Autowired(required = false) EmployeeService employeeService) {
        this.interviewRepository = Objects.requireNonNull(interviewRepository, "interviewRepository must not be null");
        this.interviewFeedbackRepository =
                Objects.requireNonNull(interviewFeedbackRepository, "interviewFeedbackRepository must not be null");
        this.jobApplicationRepository =
                Objects.requireNonNull(jobApplicationRepository, "jobApplicationRepository must not be null");
        this.jobOpeningRepository =
                Objects.requireNonNull(jobOpeningRepository, "jobOpeningRepository must not be null");
        this.candidateRepository = Objects.requireNonNull(candidateRepository, "candidateRepository must not be null");
        this.employeeService = employeeService;
    }

    @Override
    public InterviewResponse scheduleInterview(
            UUID tenantId, UUID applicationId, InterviewRequest request, String createdBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job application not found: " + applicationId));

        Interview interview = new Interview();
        interview.setTenantId(tenantId);
        interview.setApplicationId(applicationId);
        interview.setRoundName(request.roundName().trim());
        interview.setScheduledAt(request.scheduledAt());
        interview.setDurationMinutes(
                request.durationMinutes() != null && request.durationMinutes() > 0 ? request.durationMinutes() : 45);
        interview.setInterviewerId(request.interviewerId());
        interview.setMeetingLink(request.meetingLink());
        interview.setStatus(InterviewStatus.SCHEDULED);
        interview.setCreatedBy(createdBy != null ? createdBy : "system");
        interview.setUpdatedBy(interview.getCreatedBy());

        Interview saved = interviewRepository.save(interview);

        // Advance application to INTERVIEWING if currently in APPLIED or SCREENING
        if (app.getStage() == JobApplicationStage.APPLIED || app.getStage() == JobApplicationStage.SCREENING) {
            app.setStage(JobApplicationStage.INTERVIEWING);
            jobApplicationRepository.save(app);
        }

        return toResponse(saved, app);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewResponse getInterview(UUID tenantId, UUID id) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");

        Interview interview = interviewRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Interview not found: " + id));

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(interview.getApplicationId(), tenantId)
                .orElse(null);
        return toResponse(interview, app);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewResponse> listInterviewsForApplication(UUID tenantId, UUID applicationId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(applicationId, tenantId)
                .orElse(null);
        return interviewRepository
                .findAllByTenantIdAndApplicationIdOrderByScheduledAtAsc(tenantId, applicationId)
                .stream()
                .map(i -> toResponse(i, app))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewResponse> listInterviewsForInterviewer(UUID tenantId, UUID interviewerId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(interviewerId, "interviewerId must not be null");

        return interviewRepository
                .findAllByTenantIdAndInterviewerIdOrderByScheduledAtDesc(tenantId, interviewerId)
                .stream()
                .map(i -> {
                    JobApplication app = jobApplicationRepository
                            .findByIdAndTenantId(i.getApplicationId(), tenantId)
                            .orElse(null);
                    return toResponse(i, app);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewResponse> listAllInterviews(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return interviewRepository.findAllByTenantIdOrderByScheduledAtDesc(tenantId).stream()
                .map(i -> {
                    JobApplication app = jobApplicationRepository
                            .findByIdAndTenantId(i.getApplicationId(), tenantId)
                            .orElse(null);
                    return toResponse(i, app);
                })
                .toList();
    }

    @Override
    public InterviewFeedbackResponse submitFeedback(
            UUID tenantId, UUID interviewId, UUID interviewerId, InterviewFeedbackRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(interviewId, "interviewId must not be null");
        Objects.requireNonNull(interviewerId, "interviewerId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        Interview interview = interviewRepository
                .findByIdAndTenantId(interviewId, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Interview not found: " + interviewId));

        if (interviewFeedbackRepository.existsByTenantIdAndInterviewIdAndInterviewerId(
                tenantId, interviewId, interviewerId)) {
            throw new HiringConflictException("Feedback already submitted for this interview by interviewer");
        }

        if (request.rating() < 1 || request.rating() > 5) {
            throw new HiringValidationException("Rating must be between 1 and 5");
        }

        InterviewFeedback feedback = new InterviewFeedback();
        feedback.setTenantId(tenantId);
        feedback.setInterviewId(interviewId);
        feedback.setInterviewerId(interviewerId);
        feedback.setRating(request.rating());
        feedback.setRecommendation(request.recommendation());
        feedback.setStrengths(request.strengths());
        feedback.setWeaknesses(request.weaknesses());
        feedback.setComments(request.comments());
        feedback.setSubmittedAt(Instant.now());

        InterviewFeedback saved = interviewFeedbackRepository.save(feedback);

        interview.setStatus(InterviewStatus.COMPLETED);
        interviewRepository.save(interview);

        String interviewerName = resolveEmployeeName(interviewerId);
        return new InterviewFeedbackResponse(
                saved.getId(),
                saved.getInterviewId(),
                saved.getInterviewerId(),
                interviewerName,
                saved.getRating(),
                saved.getRecommendation(),
                saved.getStrengths(),
                saved.getWeaknesses(),
                saved.getComments(),
                saved.getSubmittedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewFeedbackResponse> listFeedback(UUID tenantId, UUID interviewId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(interviewId, "interviewId must not be null");

        return interviewFeedbackRepository
                .findAllByTenantIdAndInterviewIdOrderBySubmittedAtAsc(tenantId, interviewId)
                .stream()
                .map(fb -> new InterviewFeedbackResponse(
                        fb.getId(),
                        fb.getInterviewId(),
                        fb.getInterviewerId(),
                        resolveEmployeeName(fb.getInterviewerId()),
                        fb.getRating(),
                        fb.getRecommendation(),
                        fb.getStrengths(),
                        fb.getWeaknesses(),
                        fb.getComments(),
                        fb.getSubmittedAt()))
                .toList();
    }

    private InterviewResponse toResponse(Interview i, JobApplication app) {
        UUID tenantId = i.getTenantId();
        List<InterviewFeedback> feedbacks =
                interviewFeedbackRepository.findAllByTenantIdAndInterviewIdOrderBySubmittedAtAsc(tenantId, i.getId());
        int feedbackCount = feedbacks.size();
        Double avgRating = feedbacks.isEmpty()
                ? null
                : feedbacks.stream()
                        .mapToInt(InterviewFeedback::getRating)
                        .average()
                        .orElse(0.0);

        UUID candidateId = null;
        String candidateName = null;
        UUID jobOpeningId = null;
        String jobOpeningTitle = null;

        if (app != null) {
            candidateId = app.getCandidateId();
            jobOpeningId = app.getJobOpeningId();

            Candidate c = candidateRepository
                    .findByIdAndTenantId(candidateId, tenantId)
                    .orElse(null);
            if (c != null) {
                candidateName = c.getFirstName() + " " + c.getLastName();
            }

            JobOpening o = jobOpeningRepository
                    .findByIdAndTenantId(jobOpeningId, tenantId)
                    .orElse(null);
            if (o != null) {
                jobOpeningTitle = o.getTitle();
            }
        }

        String interviewerName = resolveEmployeeName(i.getInterviewerId());

        return new InterviewResponse(
                i.getId(),
                i.getApplicationId(),
                candidateId,
                candidateName,
                jobOpeningId,
                jobOpeningTitle,
                i.getRoundName(),
                i.getScheduledAt(),
                i.getDurationMinutes(),
                i.getInterviewerId(),
                interviewerName,
                i.getMeetingLink(),
                i.getStatus(),
                feedbackCount,
                avgRating,
                i.getCreatedAt());
    }

    private String resolveEmployeeName(UUID employeeId) {
        if (employeeService != null && employeeId != null) {
            try {
                EmployeeResponse emp = employeeService.get(employeeId);
                if (emp != null) {
                    return emp.firstName() + (emp.lastName() != null ? " " + emp.lastName() : "");
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
