package com.infinevo.hrms.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.org.DepartmentService;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.org.WorkLocationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobOfferServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private JobOfferRepository jobOfferRepository;
    private JobApplicationRepository jobApplicationRepository;
    private JobOpeningRepository jobOpeningRepository;
    private CandidateRepository candidateRepository;
    private EmployeeService employeeService;
    private DepartmentService departmentService;
    private DesignationService designationService;
    private WorkLocationService workLocationService;

    private JobOfferServiceImpl service;

    @BeforeEach
    void setUp() {
        jobOfferRepository = mock(JobOfferRepository.class);
        jobApplicationRepository = mock(JobApplicationRepository.class);
        jobOpeningRepository = mock(JobOpeningRepository.class);
        candidateRepository = mock(CandidateRepository.class);
        employeeService = mock(EmployeeService.class);
        departmentService = mock(DepartmentService.class);
        designationService = mock(DesignationService.class);
        workLocationService = mock(WorkLocationService.class);

        service = new JobOfferServiceImpl(
                jobOfferRepository,
                jobApplicationRepository,
                jobOpeningRepository,
                candidateRepository,
                employeeService,
                departmentService,
                designationService,
                workLocationService);
    }

    @Test
    @DisplayName("createOffer: creates offer in DRAFT status and moves application to OFFERED")
    void createOffer_success() {
        UUID appId = UUID.randomUUID();
        JobApplication app = new JobApplication();
        app.setId(appId);
        app.setTenantId(TENANT_ID);
        app.setStage(JobApplicationStage.INTERVIEWING);

        when(jobApplicationRepository.findByIdAndTenantId(appId, TENANT_ID)).thenReturn(Optional.of(app));
        when(jobOfferRepository.save(any(JobOffer.class))).thenAnswer(inv -> {
            JobOffer o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        JobOfferRequest request = new JobOfferRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("1800000.00"),
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 11, 30));

        JobOfferResponse response = service.createOffer(TENANT_ID, appId, request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(JobOfferStatus.DRAFT);
        assertThat(response.offeredCtc()).isEqualByComparingTo("1800000.00");
        assertThat(app.getStage()).isEqualTo(JobApplicationStage.OFFERED);
        verify(jobApplicationRepository).save(app);
    }

    @Test
    @DisplayName("convertToEmployee: creates core.employee, moves app to HIRED, increments filled count")
    void convertToEmployee_success() {
        UUID offerId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        UUID candId = UUID.randomUUID();
        UUID openingId = UUID.randomUUID();
        UUID newEmpId = UUID.randomUUID();

        JobOffer offer = new JobOffer();
        offer.setId(offerId);
        offer.setTenantId(TENANT_ID);
        offer.setApplicationId(appId);
        offer.setStatus(JobOfferStatus.ACCEPTED);
        offer.setTargetJoinDate(LocalDate.of(2026, 12, 1));
        offer.setDepartmentId(UUID.randomUUID());
        offer.setDesignationId(UUID.randomUUID());
        offer.setWorkLocationId(UUID.randomUUID());
        offer.setConvertedEmployeeId(null);

        JobApplication app = new JobApplication();
        app.setId(appId);
        app.setTenantId(TENANT_ID);
        app.setJobOpeningId(openingId);
        app.setCandidateId(candId);
        app.setStage(JobApplicationStage.OFFERED);

        Candidate candidate = new Candidate();
        candidate.setId(candId);
        candidate.setTenantId(TENANT_ID);
        candidate.setFirstName("Vikram");
        candidate.setLastName("Patel");
        candidate.setEmail("vikram@example.com");
        candidate.setPhone("+91 9988776655");
        candidate.setNoticePeriodDays(30);

        JobOpening opening = new JobOpening();
        opening.setId(openingId);
        opening.setTenantId(TENANT_ID);
        opening.setOpeningsCount(1);
        opening.setFilledCount(0);
        opening.setStatus(JobOpeningStatus.OPEN);

        when(jobOfferRepository.findByIdAndTenantId(offerId, TENANT_ID)).thenReturn(Optional.of(offer));
        when(jobApplicationRepository.findByIdAndTenantId(appId, TENANT_ID)).thenReturn(Optional.of(app));
        when(candidateRepository.findByIdAndTenantId(candId, TENANT_ID)).thenReturn(Optional.of(candidate));
        when(jobOpeningRepository.findByIdAndTenantId(openingId, TENANT_ID)).thenReturn(Optional.of(opening));

        EmployeeResponse mockCreated = new EmployeeResponse(
                newEmpId,
                TENANT_ID,
                "EMP-2026-101",
                "Vikram",
                null,
                "Patel",
                "MALE",
                LocalDate.of(2026, 12, 1),
                null,
                EmploymentStatus.ACTIVE,
                "vikram.work@company.com",
                null,
                true,
                offer.getDepartmentId(),
                offer.getDesignationId(),
                offer.getWorkLocationId(),
                null,
                Instant.now(),
                Instant.now());

        when(employeeService.create(any(EmployeeRequest.class))).thenReturn(mockCreated);

        ConvertToEmployeeRequest request =
                new ConvertToEmployeeRequest("EMP-2026-101", "vikram.work@company.com", "MALE");

        EmployeeResponse result = service.convertToEmployee(TENANT_ID, offerId, request, "admin");

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(newEmpId);
        assertThat(result.employeeNumber()).isEqualTo("EMP-2026-101");
        assertThat(offer.getConvertedEmployeeId()).isEqualTo(newEmpId);
        assertThat(app.getStage()).isEqualTo(JobApplicationStage.HIRED);
        assertThat(opening.getFilledCount()).isEqualTo(1);
        assertThat(opening.getStatus()).isEqualTo(JobOpeningStatus.FILLED);

        verify(employeeService).create(any(EmployeeRequest.class));
        verify(jobOfferRepository).save(offer);
        verify(jobApplicationRepository).save(app);
        verify(jobOpeningRepository).save(opening);
    }

    @Test
    @DisplayName("convertToEmployee: throws HiringValidationException when offer is not ACCEPTED")
    void convertToEmployee_notAccepted_throwsValidation() {
        UUID offerId = UUID.randomUUID();
        JobOffer offer = new JobOffer();
        offer.setId(offerId);
        offer.setTenantId(TENANT_ID);
        offer.setStatus(JobOfferStatus.DRAFT);

        when(jobOfferRepository.findByIdAndTenantId(offerId, TENANT_ID)).thenReturn(Optional.of(offer));

        ConvertToEmployeeRequest request = new ConvertToEmployeeRequest("EMP-001", null, null);

        assertThatThrownBy(() -> service.convertToEmployee(TENANT_ID, offerId, request, "admin"))
                .isInstanceOf(HiringValidationException.class)
                .hasMessageContaining("Only ACCEPTED offers can be converted to employee");
    }
}
