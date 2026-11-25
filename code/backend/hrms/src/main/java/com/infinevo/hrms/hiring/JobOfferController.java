package com.infinevo.hrms.hiring;

import com.infinevo.core.employee.EmployeeResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Job Offers and Employee Conversion (W-112).
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
@RequestMapping("/api/v1/hrms/offers")
public class JobOfferController {

    private final JobOfferService jobOfferService;

    public JobOfferController(JobOfferService jobOfferService) {
        this.jobOfferService = Objects.requireNonNull(jobOfferService, "jobOfferService must not be null");
    }

    @GetMapping
    @RequiresAction("hrms.hiring.read")
    public ApiResponse<List<JobOfferResponse>> listOffers(
            @RequestParam(name = "status", required = false) JobOfferStatus status) {
        UUID tenantId = TenantContext.require();
        List<JobOfferResponse> offers = jobOfferService.listOffers(tenantId, status);
        return ApiResponse.success("Job offers retrieved", offers);
    }

    @GetMapping("/{id}")
    @RequiresAction("hrms.hiring.read")
    public ApiResponse<JobOfferResponse> getOffer(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        JobOfferResponse offer = jobOfferService.getOffer(tenantId, id);
        return ApiResponse.success("Job offer retrieved", offer);
    }

    @PostMapping("/{id}/send")
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<JobOfferResponse> sendOffer(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        JobOfferResponse offer = jobOfferService.sendOffer(tenantId, id, "admin");
        return ApiResponse.success("Job offer sent successfully", offer);
    }

    @PostMapping("/{id}/accept")
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<JobOfferResponse> acceptOffer(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        JobOfferResponse offer = jobOfferService.acceptOffer(tenantId, id, "admin");
        return ApiResponse.success("Job offer accepted successfully", offer);
    }

    @PostMapping("/{id}/decline")
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<JobOfferResponse> declineOffer(
            @PathVariable("id") UUID id, @RequestBody(required = false) JobOfferStatusUpdateRequest request) {
        UUID tenantId = TenantContext.require();
        String reason = request != null ? request.reason() : null;
        JobOfferResponse offer = jobOfferService.declineOffer(tenantId, id, reason, "admin");
        return ApiResponse.success("Job offer declined", offer);
    }

    @PostMapping("/{id}/convert-to-employee")
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<EmployeeResponse> convertToEmployee(
            @PathVariable("id") UUID id, @Valid @RequestBody ConvertToEmployeeRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse employee = jobOfferService.convertToEmployee(tenantId, id, request, "admin");
        return ApiResponse.success("Candidate converted to employee successfully", employee);
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
