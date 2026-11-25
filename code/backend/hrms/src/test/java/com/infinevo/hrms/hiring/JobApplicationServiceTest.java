package com.infinevo.hrms.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobApplicationServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private JobApplicationRepository jobApplicationRepository;
    private JobOpeningRepository jobOpeningRepository;
    private CandidateRepository candidateRepository;

    private JobApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        jobApplicationRepository = mock(JobApplicationRepository.class);
        jobOpeningRepository = mock(JobOpeningRepository.class);
        candidateRepository = mock(CandidateRepository.class);

        service = new JobApplicationServiceImpl(jobApplicationRepository, jobOpeningRepository, candidateRepository);
    }

    @Test
    @DisplayName("apply: creates application in APPLIED stage")
    void apply_success() {
        UUID openingId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        JobOpening opening = new JobOpening();
        opening.setId(openingId);
        opening.setTenantId(TENANT_ID);
        opening.setOpeningCode("JOB-555");
        opening.setTitle("Product Designer");

        Candidate candidate = new Candidate();
        candidate.setId(candidateId);
        candidate.setTenantId(TENANT_ID);
        candidate.setFirstName("Priya");
        candidate.setLastName("Sharma");
        candidate.setEmail("priya@example.com");

        when(jobOpeningRepository.findByIdAndTenantId(openingId, TENANT_ID)).thenReturn(Optional.of(opening));
        when(candidateRepository.findByIdAndTenantId(candidateId, TENANT_ID)).thenReturn(Optional.of(candidate));
        when(jobApplicationRepository.existsByTenantIdAndJobOpeningIdAndCandidateId(TENANT_ID, openingId, candidateId))
                .thenReturn(false);
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> {
            JobApplication a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        JobApplicationResponse response = service.apply(TENANT_ID, openingId, candidateId, "admin");

        assertThat(response).isNotNull();
        assertThat(response.jobOpeningId()).isEqualTo(openingId);
        assertThat(response.candidateId()).isEqualTo(candidateId);
        assertThat(response.stage()).isEqualTo(JobApplicationStage.APPLIED);
        assertThat(response.candidateName()).isEqualTo("Priya Sharma");
        verify(jobApplicationRepository).save(any(JobApplication.class));
    }

    @Test
    @DisplayName("apply: throws HiringConflictException when candidate has already applied")
    void apply_duplicateApplication_throwsConflict() {
        UUID openingId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();

        JobOpening opening = new JobOpening();
        opening.setId(openingId);
        opening.setOpeningCode("JOB-555");

        Candidate candidate = new Candidate();
        candidate.setId(candidateId);

        when(jobOpeningRepository.findByIdAndTenantId(openingId, TENANT_ID)).thenReturn(Optional.of(opening));
        when(candidateRepository.findByIdAndTenantId(candidateId, TENANT_ID)).thenReturn(Optional.of(candidate));
        when(jobApplicationRepository.existsByTenantIdAndJobOpeningIdAndCandidateId(TENANT_ID, openingId, candidateId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.apply(TENANT_ID, openingId, candidateId, "admin"))
                .isInstanceOf(HiringConflictException.class)
                .hasMessageContaining("Candidate already applied to job opening");
    }

    @Test
    @DisplayName("changeStage: moves application to SCREENING then INTERVIEWING")
    void changeStage_progression() {
        UUID appId = UUID.randomUUID();
        JobApplication app = new JobApplication();
        app.setId(appId);
        app.setTenantId(TENANT_ID);
        app.setStage(JobApplicationStage.APPLIED);

        when(jobApplicationRepository.findByIdAndTenantId(appId, TENANT_ID)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        JobApplicationResponse updated =
                service.changeStage(TENANT_ID, appId, JobApplicationStage.SCREENING, null, "admin");
        assertThat(updated.stage()).isEqualTo(JobApplicationStage.SCREENING);

        JobApplicationResponse updated2 =
                service.changeStage(TENANT_ID, appId, JobApplicationStage.INTERVIEWING, null, "admin");
        assertThat(updated2.stage()).isEqualTo(JobApplicationStage.INTERVIEWING);
    }
}
