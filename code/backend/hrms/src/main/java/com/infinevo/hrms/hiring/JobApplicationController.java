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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Job Applications (W-112).
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
@RequestMapping("/api/v1/hrms/applications")
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;
    private final InterviewService interviewService;
    private final JobOfferService jobOfferService;

    public JobApplicationController(
            JobApplicationService jobApplicationService,
            InterviewService interviewService,
            JobOfferService jobOfferService) {
        this.jobApplicationService =
                Objects.requireNonNull(jobApplicationService, "jobApplicationService must not be null");
        this.interviewService = Objects.requireNonNull(interviewService, "interviewService must not be null");
        this.jobOfferService = Objects.requireNonNull(jobOfferService, "jobOfferService must not be null");
    }

    @GetMapping("/{id}")
    @RequiresAction("hrms.hiring.read")
    public ApiResponse<JobApplicationResponse> getApplication(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        JobApplicationResponse app = jobApplicationService.getApplication(tenantId, id);
        return ApiResponse.success("Job application retrieved", app);
    }

    @PatchMapping("/{id}/stage")
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<JobApplicationResponse> changeStage(
            @PathVariable("id") UUID id, @Valid @RequestBody JobApplicationStageUpdateRequest request) {
        UUID tenantId = TenantContext.require();
        JobApplicationResponse updated =
                jobApplicationService.changeStage(tenantId, id, request.stage(), request.rejectionReason(), "admin");
        return ApiResponse.success("Job application stage updated successfully", updated);
    }

    @PostMapping("/{id}/interviews")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<InterviewResponse> scheduleInterview(
            @PathVariable("id") UUID id, @Valid @RequestBody InterviewRequest request) {
        UUID tenantId = TenantContext.require();
        InterviewResponse interview = interviewService.scheduleInterview(tenantId, id, request, "admin");
        return ApiResponse.success("Interview round scheduled successfully", interview);
    }

    @GetMapping("/{id}/interviews")
    @RequiresAction("hrms.hiring.read")
    public ApiResponse<List<InterviewResponse>> listInterviews(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        List<InterviewResponse> interviews = interviewService.listInterviewsForApplication(tenantId, id);
        return ApiResponse.success("Interviews retrieved", interviews);
    }

    @PostMapping("/{id}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<JobOfferResponse> createOffer(
            @PathVariable("id") UUID id, @Valid @RequestBody JobOfferRequest request) {
        UUID tenantId = TenantContext.require();
        JobOfferResponse offer = jobOfferService.createOffer(tenantId, id, request, "admin");
        return ApiResponse.success("Job offer created successfully", offer);
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
