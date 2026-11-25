package com.infinevo.hrms.hiring;

import com.infinevo.hrms.project.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Job Openings (W-112).
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
@RequestMapping("/api/v1/hrms/job-openings")
public class JobOpeningController {

    private final JobOpeningService jobOpeningService;
    private final JobApplicationService jobApplicationService;

    public JobOpeningController(JobOpeningService jobOpeningService, JobApplicationService jobApplicationService) {
        this.jobOpeningService = Objects.requireNonNull(jobOpeningService, "jobOpeningService must not be null");
        this.jobApplicationService =
                Objects.requireNonNull(jobApplicationService, "jobApplicationService must not be null");
    }

    @GetMapping
    @RequiresAction("hrms.hiring.read")
    public ApiResponse<List<JobOpeningResponse>> listOpenings(
            @RequestParam(name = "status", required = false) JobOpeningStatus status) {
        UUID tenantId = TenantContext.require();
        List<JobOpeningResponse> openings = jobOpeningService.listOpenings(tenantId, status);
        return ApiResponse.success("Job openings retrieved", openings);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<JobOpeningResponse> createOpening(@Valid @RequestBody JobOpeningRequest request) {
        UUID tenantId = TenantContext.require();
        JobOpeningResponse opening = jobOpeningService.createOpening(tenantId, request, "admin");
        return ApiResponse.success("Job opening created successfully", opening);
    }

    @GetMapping("/{id}")
    @RequiresAction("hrms.hiring.read")
    public ApiResponse<JobOpeningResponse> getOpening(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        JobOpeningResponse opening = jobOpeningService.getOpening(tenantId, id);
        return ApiResponse.success("Job opening retrieved", opening);
    }

    @PutMapping("/{id}")
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<JobOpeningResponse> updateOpening(
            @PathVariable("id") UUID id, @Valid @RequestBody JobOpeningRequest request) {
        UUID tenantId = TenantContext.require();
        JobOpeningResponse updated = jobOpeningService.updateOpening(tenantId, id, request, "admin");
        return ApiResponse.success("Job opening updated successfully", updated);
    }

    @PatchMapping("/{id}/status")
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<JobOpeningResponse> changeStatus(
            @PathVariable("id") UUID id, @Valid @RequestBody JobOpeningStatusUpdateRequest request) {
        UUID tenantId = TenantContext.require();
        JobOpeningResponse updated = jobOpeningService.changeStatus(tenantId, id, request.status(), "admin");
        return ApiResponse.success("Job opening status updated successfully", updated);
    }

    @PostMapping("/{id}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<JobApplicationResponse> apply(
            @PathVariable("id") UUID id, @Valid @RequestBody JobApplicationRequest request) {
        UUID tenantId = TenantContext.require();
        JobApplicationResponse app = jobApplicationService.apply(tenantId, id, request.candidateId(), "admin");
        return ApiResponse.success("Candidate application submitted successfully", app);
    }

    @GetMapping("/{id}/applications")
    @RequiresAction("hrms.hiring.read")
    public ApiResponse<List<JobApplicationResponse>> listApplications(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        List<JobApplicationResponse> apps = jobApplicationService.listApplicationsByOpening(tenantId, id);
        return ApiResponse.success("Applications retrieved", apps);
    }

    @ExceptionHandler(HiringNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(HiringNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HiringConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(HiringConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HiringValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(HiringValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiErrorResponse.validation(errors, traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
