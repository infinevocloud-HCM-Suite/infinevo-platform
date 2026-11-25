package com.infinevo.hrms.hiring;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.DepartmentResponse;
import com.infinevo.core.org.DepartmentService;
import com.infinevo.core.org.DesignationResponse;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link JobOpeningService} (W-112).
 */
@Service
@Transactional
public class JobOpeningServiceImpl implements JobOpeningService {

    private final JobOpeningRepository jobOpeningRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final DepartmentService departmentService;
    private final DesignationService designationService;
    private final WorkLocationService workLocationService;
    private final EmployeeService employeeService;

    @Autowired
    public JobOpeningServiceImpl(
            JobOpeningRepository jobOpeningRepository,
            JobApplicationRepository jobApplicationRepository,
            @Autowired(required = false) DepartmentService departmentService,
            @Autowired(required = false) DesignationService designationService,
            @Autowired(required = false) WorkLocationService workLocationService,
            @Autowired(required = false) EmployeeService employeeService) {
        this.jobOpeningRepository =
                Objects.requireNonNull(jobOpeningRepository, "jobOpeningRepository must not be null");
        this.jobApplicationRepository =
                Objects.requireNonNull(jobApplicationRepository, "jobApplicationRepository must not be null");
        this.departmentService = departmentService;
        this.designationService = designationService;
        this.workLocationService = workLocationService;
        this.employeeService = employeeService;
    }

    @Override
    public JobOpeningResponse createOpening(UUID tenantId, JobOpeningRequest request, String createdBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (jobOpeningRepository.existsByTenantIdAndOpeningCode(
                tenantId, request.openingCode().trim())) {
            throw new HiringConflictException("Job opening code already exists: " + request.openingCode());
        }

        validateRanges(request);

        JobOpening opening = new JobOpening();
        opening.setTenantId(tenantId);
        opening.setOpeningCode(request.openingCode().trim());
        opening.setTitle(request.title().trim());
        opening.setDepartmentId(request.departmentId());
        opening.setDesignationId(request.designationId());
        opening.setWorkLocationId(request.workLocationId());
        opening.setHiringManagerId(request.hiringManagerId());
        if (request.employmentType() != null) {
            opening.setEmploymentType(request.employmentType());
        }
        opening.setOpeningsCount(request.openingsCount() != null ? request.openingsCount() : 1);
        opening.setFilledCount(0);
        opening.setMinSalary(request.minSalary());
        opening.setMaxSalary(request.maxSalary());
        opening.setMinExperienceYears(request.minExperienceYears());
        opening.setMaxExperienceYears(request.maxExperienceYears());
        opening.setDescription(request.description());
        opening.setStatus(JobOpeningStatus.DRAFT);
        opening.setTargetDate(request.targetDate());
        opening.setCreatedBy(createdBy != null ? createdBy : "system");
        opening.setUpdatedBy(opening.getCreatedBy());

        JobOpening saved = jobOpeningRepository.save(opening);
        return toResponse(saved);
    }

    @Override
    public JobOpeningResponse updateOpening(UUID tenantId, UUID id, JobOpeningRequest request, String updatedBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(request, "request must not be null");

        JobOpening opening = jobOpeningRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job opening not found: " + id));

        String newCode = request.openingCode().trim();
        if (!opening.getOpeningCode().equalsIgnoreCase(newCode)
                && jobOpeningRepository.existsByTenantIdAndOpeningCode(tenantId, newCode)) {
            throw new HiringConflictException("Job opening code already exists: " + newCode);
        }

        validateRanges(request);

        opening.setOpeningCode(newCode);
        opening.setTitle(request.title().trim());
        opening.setDepartmentId(request.departmentId());
        opening.setDesignationId(request.designationId());
        opening.setWorkLocationId(request.workLocationId());
        opening.setHiringManagerId(request.hiringManagerId());
        if (request.employmentType() != null) {
            opening.setEmploymentType(request.employmentType());
        }
        if (request.openingsCount() != null) {
            opening.setOpeningsCount(request.openingsCount());
        }
        opening.setMinSalary(request.minSalary());
        opening.setMaxSalary(request.maxSalary());
        opening.setMinExperienceYears(request.minExperienceYears());
        opening.setMaxExperienceYears(request.maxExperienceYears());
        opening.setDescription(request.description());
        opening.setTargetDate(request.targetDate());
        opening.setUpdatedBy(updatedBy != null ? updatedBy : "system");

        JobOpening saved = jobOpeningRepository.save(opening);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public JobOpeningResponse getOpening(UUID tenantId, UUID id) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");

        JobOpening opening = jobOpeningRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job opening not found: " + id));
        return toResponse(opening);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobOpeningResponse> listOpenings(UUID tenantId, JobOpeningStatus status) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        List<JobOpening> openings = status != null
                ? jobOpeningRepository.findAllByTenantIdAndStatus(tenantId, status)
                : jobOpeningRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);

        return openings.stream().map(this::toResponse).toList();
    }

    @Override
    public JobOpeningResponse changeStatus(UUID tenantId, UUID id, JobOpeningStatus newStatus, String updatedBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        JobOpening opening = jobOpeningRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job opening not found: " + id));

        opening.setStatus(newStatus);
        opening.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        JobOpening saved = jobOpeningRepository.save(opening);
        return toResponse(saved);
    }

    private void validateRanges(JobOpeningRequest request) {
        if (request.minSalary() != null
                && request.maxSalary() != null
                && request.minSalary().compareTo(request.maxSalary()) > 0) {
            throw new HiringValidationException("minSalary cannot be greater than maxSalary");
        }
        if (request.minExperienceYears() != null
                && request.maxExperienceYears() != null
                && request.minExperienceYears() > request.maxExperienceYears()) {
            throw new HiringValidationException("minExperienceYears cannot be greater than maxExperienceYears");
        }
    }

    private JobOpeningResponse toResponse(JobOpening o) {
        UUID tenantId = o.getTenantId();
        UUID id = o.getId();

        long totalCandidates = jobApplicationRepository.countByTenantIdAndJobOpeningId(tenantId, id);
        long appliedCount = jobApplicationRepository.countByTenantIdAndJobOpeningIdAndStage(
                tenantId, id, JobApplicationStage.APPLIED);
        long screeningCount = jobApplicationRepository.countByTenantIdAndJobOpeningIdAndStage(
                tenantId, id, JobApplicationStage.SCREENING);
        long interviewingCount = jobApplicationRepository.countByTenantIdAndJobOpeningIdAndStage(
                tenantId, id, JobApplicationStage.INTERVIEWING);
        long offeredCount = jobApplicationRepository.countByTenantIdAndJobOpeningIdAndStage(
                tenantId, id, JobApplicationStage.OFFERED);
        long hiredCount = jobApplicationRepository.countByTenantIdAndJobOpeningIdAndStage(
                tenantId, id, JobApplicationStage.HIRED);
        long rejectedCount = jobApplicationRepository.countByTenantIdAndJobOpeningIdAndStage(
                tenantId, id, JobApplicationStage.REJECTED);

        String deptName = null;
        if (departmentService != null && o.getDepartmentId() != null) {
            try {
                DepartmentResponse dept = departmentService.get(o.getDepartmentId());
                if (dept != null) {
                    deptName = dept.name();
                }
            } catch (Exception ignored) {
            }
        }

        String desigTitle = null;
        if (designationService != null && o.getDesignationId() != null) {
            try {
                DesignationResponse desig = designationService.get(o.getDesignationId());
                if (desig != null) {
                    desigTitle = desig.name();
                }
            } catch (Exception ignored) {
            }
        }

        String locName = null;
        if (workLocationService != null && o.getWorkLocationId() != null) {
            try {
                WorkLocationResponse loc = workLocationService.get(o.getWorkLocationId());
                if (loc != null) {
                    locName = loc.name();
                }
            } catch (Exception ignored) {
            }
        }

        String managerName = null;
        if (employeeService != null && o.getHiringManagerId() != null) {
            try {
                EmployeeResponse emp = employeeService.get(o.getHiringManagerId());
                if (emp != null) {
                    managerName = emp.firstName() + (emp.lastName() != null ? " " + emp.lastName() : "");
                }
            } catch (Exception ignored) {
            }
        }

        return new JobOpeningResponse(
                o.getId(),
                o.getOpeningCode(),
                o.getTitle(),
                o.getDepartmentId(),
                deptName,
                o.getDesignationId(),
                desigTitle,
                o.getWorkLocationId(),
                locName,
                o.getHiringManagerId(),
                managerName,
                o.getEmploymentType(),
                o.getOpeningsCount(),
                o.getFilledCount(),
                o.getMinSalary(),
                o.getMaxSalary(),
                o.getMinExperienceYears(),
                o.getMaxExperienceYears(),
                o.getDescription(),
                o.getStatus(),
                o.getTargetDate(),
                totalCandidates,
                appliedCount,
                screeningCount,
                interviewingCount,
                offeredCount,
                hiredCount,
                rejectedCount,
                o.getCreatedAt(),
                o.getUpdatedAt());
    }
}
