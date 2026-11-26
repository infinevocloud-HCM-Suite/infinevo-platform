package com.infinevo.hrms.survey;

import com.infinevo.hrms.project.ApiResponse;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hrms/surveys")
public class SurveyController {

    private final SurveyService surveyService;
    private final SurveyAnalyticsService analyticsService;

    public SurveyController(SurveyService surveyService, SurveyAnalyticsService analyticsService) {
        this.surveyService = Objects.requireNonNull(surveyService, "surveyService must not be null");
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService must not be null");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('hrms.survey.read')")
    public ApiResponse<List<SurveyResponseDto>> listSurveys(
            @RequestParam(name = "status", required = false) SurveyStatus status) {
        UUID tenantId = TenantContext.require();
        List<SurveyResponseDto> surveys = surveyService.listSurveys(tenantId, status);
        return ApiResponse.success(surveys);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('hrms.survey.manage')")
    public ApiResponse<SurveyResponseDto> createSurvey(@RequestBody SurveyRequest request, Principal principal) {
        UUID tenantId = TenantContext.require();
        String user = principal != null ? principal.getName() : "system";
        SurveyResponseDto created = surveyService.createSurvey(tenantId, request, user);
        return ApiResponse.success("Survey created successfully", created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('hrms.survey.read')")
    public ApiResponse<SurveyResponseDto> getSurvey(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        SurveyResponseDto survey = surveyService.getSurvey(tenantId, id);
        return ApiResponse.success(survey);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('hrms.survey.manage')")
    public ApiResponse<SurveyResponseDto> updateStatus(
            @PathVariable("id") UUID id, @RequestBody SurveyStatusUpdateRequest request, Principal principal) {
        UUID tenantId = TenantContext.require();
        String user = principal != null ? principal.getName() : "system";
        SurveyResponseDto updated = surveyService.updateStatus(tenantId, id, request.status(), user);
        return ApiResponse.success("Survey status updated", updated);
    }

    @GetMapping("/{id}/results")
    @PreAuthorize("hasAuthority('hrms.survey.read')")
    public ApiResponse<SurveyResultsResponse> getResults(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        SurveyResultsResponse results = analyticsService.getResults(tenantId, id);
        return ApiResponse.success(results);
    }

    @ExceptionHandler(SurveyNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(SurveyNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(SurveyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(SurveyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(SurveyValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(SurveyValidationException e) {
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
