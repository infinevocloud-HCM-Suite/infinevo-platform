package com.infinevo.hrms.hiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.DepartmentService;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.org.WorkLocationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobOpeningServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private JobOpeningRepository jobOpeningRepository;
    private JobApplicationRepository jobApplicationRepository;
    private DepartmentService departmentService;
    private DesignationService designationService;
    private WorkLocationService workLocationService;
    private EmployeeService employeeService;

    private JobOpeningServiceImpl service;

    @BeforeEach
    void setUp() {
        jobOpeningRepository = mock(JobOpeningRepository.class);
        jobApplicationRepository = mock(JobApplicationRepository.class);
        departmentService = mock(DepartmentService.class);
        designationService = mock(DesignationService.class);
        workLocationService = mock(WorkLocationService.class);
        employeeService = mock(EmployeeService.class);

        service = new JobOpeningServiceImpl(
                jobOpeningRepository,
                jobApplicationRepository,
                departmentService,
                designationService,
                workLocationService,
                employeeService);
    }

    @Test
    @DisplayName("createOpening: saves requisition in DRAFT status with stage counters initialized")
    void createOpening_success() {
        JobOpeningRequest request = new JobOpeningRequest(
                "JOB-2026-001",
                "Senior Backend Engineer",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                JobOpeningEmploymentType.FULL_TIME,
                3,
                new BigDecimal("2000000.00"),
                new BigDecimal("3000000.00"),
                3,
                8,
                "Java / Spring Boot specialist",
                LocalDate.of(2026, 12, 31));

        when(jobOpeningRepository.existsByTenantIdAndOpeningCode(TENANT_ID, "JOB-2026-001"))
                .thenReturn(false);
        when(jobOpeningRepository.save(any(JobOpening.class))).thenAnswer(invocation -> {
            JobOpening o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        JobOpeningResponse response = service.createOpening(TENANT_ID, request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.openingCode()).isEqualTo("JOB-2026-001");
        assertThat(response.title()).isEqualTo("Senior Backend Engineer");
        assertThat(response.status()).isEqualTo(JobOpeningStatus.DRAFT);
        assertThat(response.openingsCount()).isEqualTo(3);
        assertThat(response.filledCount()).isEqualTo(0);
        assertThat(response.totalCandidates()).isEqualTo(0);
        verify(jobOpeningRepository).save(any(JobOpening.class));
    }

    @Test
    @DisplayName("createOpening: throws HiringConflictException when openingCode already exists")
    void createOpening_duplicateCode_throwsConflict() {
        JobOpeningRequest request = new JobOpeningRequest(
                "JOB-001", "Engineer", null, null, null, null, null, 1, null, null, null, null, null, null);

        when(jobOpeningRepository.existsByTenantIdAndOpeningCode(TENANT_ID, "JOB-001"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createOpening(TENANT_ID, request, "admin"))
                .isInstanceOf(HiringConflictException.class)
                .hasMessageContaining("Job opening code already exists");
    }

    @Test
    @DisplayName("createOpening: throws HiringValidationException when minSalary > maxSalary")
    void createOpening_invalidSalaryRange_throwsValidation() {
        JobOpeningRequest request = new JobOpeningRequest(
                "JOB-002",
                "Engineer",
                null,
                null,
                null,
                null,
                null,
                1,
                new BigDecimal("5000000.00"),
                new BigDecimal("2000000.00"),
                null,
                null,
                null,
                null);

        when(jobOpeningRepository.existsByTenantIdAndOpeningCode(TENANT_ID, "JOB-002"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createOpening(TENANT_ID, request, "admin"))
                .isInstanceOf(HiringValidationException.class)
                .hasMessageContaining("minSalary cannot be greater than maxSalary");
    }

    @Test
    @DisplayName("changeStatus: transitions status from DRAFT to OPEN")
    void changeStatus_draftToOpen() {
        UUID openingId = UUID.randomUUID();
        JobOpening opening = new JobOpening();
        opening.setId(openingId);
        opening.setTenantId(TENANT_ID);
        opening.setOpeningCode("JOB-100");
        opening.setTitle("DevOps Lead");
        opening.setStatus(JobOpeningStatus.DRAFT);
        opening.setOpeningsCount(2);

        when(jobOpeningRepository.findByIdAndTenantId(openingId, TENANT_ID)).thenReturn(Optional.of(opening));
        when(jobOpeningRepository.save(any(JobOpening.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobOpeningResponse updated = service.changeStatus(TENANT_ID, openingId, JobOpeningStatus.OPEN, "admin");

        assertThat(updated.status()).isEqualTo(JobOpeningStatus.OPEN);
        assertThat(opening.getStatus()).isEqualTo(JobOpeningStatus.OPEN);
    }
}
