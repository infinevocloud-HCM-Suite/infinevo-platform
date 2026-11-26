package com.infinevo.hrms.survey;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.hrms.project.ApiResponse;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/surveys")
public class MySurveyController {

    private final SurveyService surveyService;
    private final EmployeeService employeeService;

    public MySurveyController(SurveyService surveyService, EmployeeService employeeService) {
        this.surveyService = Objects.requireNonNull(surveyService, "surveyService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('hrms.survey.respond')")
    public ApiResponse<List<ActiveSurveyDto>> listActiveSurveys() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse currentEmployee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new SurveyValidationException("Current employee profile could not be resolved"));

        List<ActiveSurveyDto> surveys = surveyService.listActiveSurveysForEmployee(tenantId, currentEmployee);
        return ApiResponse.success(surveys);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('hrms.survey.respond')")
    public ApiResponse<SurveyResponseDto> getSurveyForTaking(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        SurveyResponseDto survey = surveyService.getSurvey(tenantId, id);
        return ApiResponse.success(survey);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('hrms.survey.respond')")
    public ApiResponse<UUID> submitSurvey(@PathVariable("id") UUID id, @RequestBody SurveySubmissionRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse currentEmployee = employeeService
                .currentEmployee()
                .orElseThrow(() -> new SurveyValidationException("Current employee profile could not be resolved"));

        UUID responseId = surveyService.submitSurvey(tenantId, id, currentEmployee, request);
        return ApiResponse.success("Survey response submitted successfully", responseId);
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
