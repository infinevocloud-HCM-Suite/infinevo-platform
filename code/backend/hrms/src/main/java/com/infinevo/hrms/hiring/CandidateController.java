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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Candidates (W-112).
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
@RequestMapping("/api/v1/hrms/candidates")
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = Objects.requireNonNull(candidateService, "candidateService must not be null");
    }

    @GetMapping
    @RequiresAction("hrms.hiring.read")
    public ApiResponse<List<CandidateResponse>> listCandidates(
            @RequestParam(name = "q", required = false) String query) {
        UUID tenantId = TenantContext.require();
        List<CandidateResponse> candidates = candidateService.listCandidates(tenantId, query);
        return ApiResponse.success("Candidates retrieved", candidates);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("hrms.hiring.manage")
    public ApiResponse<CandidateResponse> createCandidate(@Valid @RequestBody CandidateRequest request) {
        UUID tenantId = TenantContext.require();
        CandidateResponse candidate = candidateService.createCandidate(tenantId, request, "admin");
        return ApiResponse.success("Candidate profile created successfully", candidate);
    }

    @GetMapping("/{id}")
    @RequiresAction("hrms.hiring.read")
    public ApiResponse<CandidateResponse> getCandidate(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        CandidateResponse candidate = candidateService.getCandidate(tenantId, id);
        return ApiResponse.success("Candidate profile retrieved", candidate);
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
