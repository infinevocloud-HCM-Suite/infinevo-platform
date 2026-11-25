package com.infinevo.hrms.hiring;

import com.infinevo.core.employee.EmployeeRequest;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.core.employee.detail.EmploymentType;
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
 * Implementation of {@link JobOfferService} (W-112).
 */
@Service
@Transactional
public class JobOfferServiceImpl implements JobOfferService {

    private final JobOfferRepository jobOfferRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobOpeningRepository jobOpeningRepository;
    private final CandidateRepository candidateRepository;
    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    private final DesignationService designationService;
    private final WorkLocationService workLocationService;

    @Autowired
    public JobOfferServiceImpl(
            JobOfferRepository jobOfferRepository,
            JobApplicationRepository jobApplicationRepository,
            JobOpeningRepository jobOpeningRepository,
            CandidateRepository candidateRepository,
            @Autowired(required = false) EmployeeService employeeService,
            @Autowired(required = false) DepartmentService departmentService,
            @Autowired(required = false) DesignationService designationService,
            @Autowired(required = false) WorkLocationService workLocationService) {
        this.jobOfferRepository = Objects.requireNonNull(jobOfferRepository, "jobOfferRepository must not be null");
        this.jobApplicationRepository =
                Objects.requireNonNull(jobApplicationRepository, "jobApplicationRepository must not be null");
        this.jobOpeningRepository =
                Objects.requireNonNull(jobOpeningRepository, "jobOpeningRepository must not be null");
        this.candidateRepository = Objects.requireNonNull(candidateRepository, "candidateRepository must not be null");
        this.employeeService = employeeService;
        this.departmentService = departmentService;
        this.designationService = designationService;
        this.workLocationService = workLocationService;
    }

    @Override
    public JobOfferResponse createOffer(UUID tenantId, UUID applicationId, JobOfferRequest request, String createdBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(applicationId, "applicationId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job application not found: " + applicationId));

        JobOffer offer = new JobOffer();
        offer.setTenantId(tenantId);
        offer.setApplicationId(applicationId);
        offer.setDesignationId(request.designationId());
        offer.setDepartmentId(request.departmentId());
        offer.setWorkLocationId(request.workLocationId());
        offer.setOfferedCtc(request.offeredCtc());
        offer.setTargetJoinDate(request.targetJoinDate());
        offer.setOfferValidUntil(request.offerValidUntil());
        offer.setStatus(JobOfferStatus.DRAFT);
        offer.setCreatedBy(createdBy != null ? createdBy : "system");
        offer.setUpdatedBy(offer.getCreatedBy());

        JobOffer saved = jobOfferRepository.save(offer);

        app.setStage(JobApplicationStage.OFFERED);
        jobApplicationRepository.save(app);

        return toResponse(saved, app);
    }

    @Override
    @Transactional(readOnly = true)
    public JobOfferResponse getOffer(UUID tenantId, UUID id) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");

        JobOffer offer = jobOfferRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job offer not found: " + id));

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(offer.getApplicationId(), tenantId)
                .orElse(null);
        return toResponse(offer, app);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobOfferResponse> listOffers(UUID tenantId, JobOfferStatus status) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        List<JobOffer> offers = status != null
                ? jobOfferRepository.findAllByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)
                : jobOfferRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);

        return offers.stream()
                .map(o -> {
                    JobApplication app = jobApplicationRepository
                            .findByIdAndTenantId(o.getApplicationId(), tenantId)
                            .orElse(null);
                    return toResponse(o, app);
                })
                .toList();
    }

    @Override
    public JobOfferResponse sendOffer(UUID tenantId, UUID id, String updatedBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");

        JobOffer offer = jobOfferRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job offer not found: " + id));

        offer.setStatus(JobOfferStatus.SENT);
        offer.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        JobOffer saved = jobOfferRepository.save(offer);

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(saved.getApplicationId(), tenantId)
                .orElse(null);
        return toResponse(saved, app);
    }

    @Override
    public JobOfferResponse acceptOffer(UUID tenantId, UUID id, String updatedBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");

        JobOffer offer = jobOfferRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job offer not found: " + id));

        offer.setStatus(JobOfferStatus.ACCEPTED);
        offer.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        JobOffer saved = jobOfferRepository.save(offer);

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(saved.getApplicationId(), tenantId)
                .orElse(null);
        return toResponse(saved, app);
    }

    @Override
    public JobOfferResponse declineOffer(UUID tenantId, UUID id, String reason, String updatedBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");

        JobOffer offer = jobOfferRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job offer not found: " + id));

        offer.setStatus(JobOfferStatus.DECLINED);
        offer.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        JobOffer saved = jobOfferRepository.save(offer);

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(saved.getApplicationId(), tenantId)
                .orElse(null);
        if (app != null) {
            app.setStage(JobApplicationStage.REJECTED);
            app.setRejectionReason(reason != null ? reason : "Candidate declined offer");
            jobApplicationRepository.save(app);
        }

        return toResponse(saved, app);
    }

    @Override
    public EmployeeResponse convertToEmployee(UUID tenantId, UUID id, ConvertToEmployeeRequest request, String actor) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(request, "request must not be null");

        JobOffer offer = jobOfferRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Job offer not found: " + id));

        if (offer.getStatus() != JobOfferStatus.ACCEPTED) {
            throw new HiringValidationException(
                    "Only ACCEPTED offers can be converted to employee. Current status: " + offer.getStatus());
        }

        if (offer.getConvertedEmployeeId() != null) {
            throw new HiringConflictException(
                    "Offer has already been converted to employee: " + offer.getConvertedEmployeeId());
        }

        JobApplication app = jobApplicationRepository
                .findByIdAndTenantId(offer.getApplicationId(), tenantId)
                .orElseThrow(
                        () -> new HiringNotFoundException("Job application not found: " + offer.getApplicationId()));

        Candidate candidate = candidateRepository
                .findByIdAndTenantId(app.getCandidateId(), tenantId)
                .orElseThrow(() -> new HiringNotFoundException("Candidate not found: " + app.getCandidateId()));

        if (employeeService == null) {
            throw new IllegalStateException("EmployeeService is not available in current context");
        }

        String workEmail = request.workEmail() != null && !request.workEmail().isBlank()
                ? request.workEmail().trim()
                : candidate.getEmail();

        EmployeeRequest empReq = new EmployeeRequest(
                request.employeeNumber().trim(),
                candidate.getFirstName(),
                null,
                candidate.getLastName(),
                request.gender() != null ? request.gender() : "UNDISCLOSED",
                offer.getTargetJoinDate(),
                null,
                EmploymentStatus.ACTIVE,
                workEmail,
                candidate.getPhone(),
                Boolean.TRUE,
                offer.getDepartmentId(),
                offer.getDesignationId(),
                offer.getWorkLocationId(),
                EmploymentType.PERMANENT,
                null,
                candidate.getNoticePeriodDays(),
                null,
                null);

        EmployeeResponse created = employeeService.create(empReq);

        offer.setConvertedEmployeeId(created.id());
        offer.setUpdatedBy(actor != null ? actor : "system");
        jobOfferRepository.save(offer);

        app.setStage(JobApplicationStage.HIRED);
        jobApplicationRepository.save(app);

        JobOpening opening = jobOpeningRepository
                .findByIdAndTenantId(app.getJobOpeningId(), tenantId)
                .orElse(null);
        if (opening != null) {
            int newFilled = opening.getFilledCount() + 1;
            opening.setFilledCount(newFilled);
            if (newFilled >= opening.getOpeningsCount()) {
                opening.setStatus(JobOpeningStatus.FILLED);
            }
            jobOpeningRepository.save(opening);
        }

        return created;
    }

    private JobOfferResponse toResponse(JobOffer o, JobApplication app) {
        UUID tenantId = o.getTenantId();
        UUID candidateId = null;
        String candidateName = null;
        String candidateEmail = null;
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
                candidateEmail = c.getEmail();
            }

            JobOpening opening = jobOpeningRepository
                    .findByIdAndTenantId(jobOpeningId, tenantId)
                    .orElse(null);
            if (opening != null) {
                jobOpeningTitle = opening.getTitle();
            }
        }

        String desigTitle = null;
        if (designationService != null && o.getDesignationId() != null) {
            try {
                DesignationResponse d = designationService.get(o.getDesignationId());
                if (d != null) {
                    desigTitle = d.name();
                }
            } catch (Exception ignored) {
            }
        }

        String deptName = null;
        if (departmentService != null && o.getDepartmentId() != null) {
            try {
                DepartmentResponse d = departmentService.get(o.getDepartmentId());
                if (d != null) {
                    deptName = d.name();
                }
            } catch (Exception ignored) {
            }
        }

        String locName = null;
        if (workLocationService != null && o.getWorkLocationId() != null) {
            try {
                WorkLocationResponse l = workLocationService.get(o.getWorkLocationId());
                if (l != null) {
                    locName = l.name();
                }
            } catch (Exception ignored) {
            }
        }

        return new JobOfferResponse(
                o.getId(),
                o.getApplicationId(),
                candidateId,
                candidateName,
                candidateEmail,
                jobOpeningId,
                jobOpeningTitle,
                o.getDesignationId(),
                desigTitle,
                o.getDepartmentId(),
                deptName,
                o.getWorkLocationId(),
                locName,
                o.getOfferedCtc(),
                o.getTargetJoinDate(),
                o.getOfferValidUntil(),
                o.getStatus(),
                o.getConvertedEmployeeId(),
                o.getCreatedAt(),
                o.getUpdatedAt());
    }
}
