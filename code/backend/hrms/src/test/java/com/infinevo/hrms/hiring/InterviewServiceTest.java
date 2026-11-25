package com.infinevo.hrms.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterviewServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private InterviewRepository interviewRepository;
    private InterviewFeedbackRepository interviewFeedbackRepository;
    private JobApplicationRepository jobApplicationRepository;
    private JobOpeningRepository jobOpeningRepository;
    private CandidateRepository candidateRepository;
    private EmployeeService employeeService;

    private InterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        interviewRepository = mock(InterviewRepository.class);
        interviewFeedbackRepository = mock(InterviewFeedbackRepository.class);
        jobApplicationRepository = mock(JobApplicationRepository.class);
        jobOpeningRepository = mock(JobOpeningRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        employeeService = mock(EmployeeService.class);

        service = new InterviewServiceImpl(
                interviewRepository,
                interviewFeedbackRepository,
                jobApplicationRepository,
                jobOpeningRepository,
                candidateRepository,
                employeeService);
    }

    @Test
    @DisplayName("scheduleInterview: creates round and auto-advances application to INTERVIEWING")
    void scheduleInterview_success() {
        UUID appId = UUID.randomUUID();
        UUID interviewerId = UUID.randomUUID();

        JobApplication app = new JobApplication();
        app.setId(appId);
        app.setTenantId(TENANT_ID);
        app.setStage(JobApplicationStage.SCREENING);

        when(jobApplicationRepository.findByIdAndTenantId(appId, TENANT_ID)).thenReturn(Optional.of(app));
        when(interviewRepository.save(any(Interview.class))).thenAnswer(inv -> {
            Interview i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });

        InterviewRequest request = new InterviewRequest(
                "Technical Round 1",
                Instant.now().plusSeconds(3600),
                60,
                interviewerId,
                "https://meet.google.com/abc-xyz");

        InterviewResponse response = service.scheduleInterview(TENANT_ID, appId, request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.roundName()).isEqualTo("Technical Round 1");
        assertThat(response.durationMinutes()).isEqualTo(60);
        assertThat(response.status()).isEqualTo(InterviewStatus.SCHEDULED);
        assertThat(app.getStage()).isEqualTo(JobApplicationStage.INTERVIEWING);
        verify(jobApplicationRepository).save(app);
    }

    @Test
    @DisplayName("submitFeedback: saves 1-5 scorecard and marks interview COMPLETED")
    void submitFeedback_success() {
        UUID interviewId = UUID.randomUUID();
        UUID interviewerId = UUID.randomUUID();

        Interview interview = new Interview();
        interview.setId(interviewId);
        interview.setTenantId(TENANT_ID);
        interview.setStatus(InterviewStatus.SCHEDULED);

        when(interviewRepository.findByIdAndTenantId(interviewId, TENANT_ID)).thenReturn(Optional.of(interview));
        when(interviewFeedbackRepository.existsByTenantIdAndInterviewIdAndInterviewerId(
                        TENANT_ID, interviewId, interviewerId))
                .thenReturn(false);
        when(interviewFeedbackRepository.save(any(InterviewFeedback.class))).thenAnswer(inv -> {
            InterviewFeedback fb = inv.getArgument(0);
            fb.setId(UUID.randomUUID());
            return fb;
        });

        InterviewFeedbackRequest request = new InterviewFeedbackRequest(
                5,
                ScorecardRecommendation.STRONG_YES,
                "Outstanding system design skills",
                "None observed",
                "Recommend fast-tracking to offer stage");

        InterviewFeedbackResponse response = service.submitFeedback(TENANT_ID, interviewId, interviewerId, request);

        assertThat(response).isNotNull();
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.recommendation()).isEqualTo(ScorecardRecommendation.STRONG_YES);
        assertThat(interview.getStatus()).isEqualTo(InterviewStatus.COMPLETED);
    }

    @Test
    @DisplayName("submitFeedback: throws HiringConflictException when duplicate feedback is submitted")
    void submitFeedback_duplicate_throwsConflict() {
        UUID interviewId = UUID.randomUUID();
        UUID interviewerId = UUID.randomUUID();

        Interview interview = new Interview();
        interview.setId(interviewId);

        when(interviewRepository.findByIdAndTenantId(interviewId, TENANT_ID)).thenReturn(Optional.of(interview));
        when(interviewFeedbackRepository.existsByTenantIdAndInterviewIdAndInterviewerId(
                        TENANT_ID, interviewId, interviewerId))
                .thenReturn(true);

        InterviewFeedbackRequest request =
                new InterviewFeedbackRequest(4, ScorecardRecommendation.YES, null, null, null);

        assertThatThrownBy(() -> service.submitFeedback(TENANT_ID, interviewId, interviewerId, request))
                .isInstanceOf(HiringConflictException.class)
                .hasMessageContaining("Feedback already submitted");
    }
}
