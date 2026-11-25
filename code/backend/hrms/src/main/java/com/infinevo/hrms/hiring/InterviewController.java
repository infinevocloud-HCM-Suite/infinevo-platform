package com.infinevo.hrms.hiring;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
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
import org.springframework.beans.factory.annotation.Autowired;
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
 * REST controller for Interviews and Scorecards (W-112).
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
@RequestMapping("/api/v1/hrms/interviews")
public class InterviewController {

    private final InterviewService interviewService;
    private final EmployeeService employeeService;

    public InterviewController(
            InterviewService interviewService, @Autowired(required = false) EmployeeService employeeService) {
        this.interviewService = Objects.requireNonNull(interviewService, "interviewService must not be null");
        this.employeeService = employeeService;
    }

    @GetMapping
    @RequiresAction("hrms.hiring.interview")
    public ApiResponse<List<InterviewResponse>> listInterviews(
            @RequestParam(name = "mine", defaultValue = "false") boolean mine) {
        UUID tenantId = TenantContext.require();
        if (mine && employeeService != null) {
            EmployeeResponse emp = employeeService.currentEmployee().orElse(null);
            if (emp != null) {
                List<InterviewResponse> mineList = interviewService.listInterviewsForInterviewer(tenantId, emp.id());
                return ApiResponse.success("My interviews retrieved", mineList);
            }
        }
        List<InterviewResponse> all = interviewService.listAllInterviews(tenantId);
        return ApiResponse.success("Interviews retrieved", all);
    }

    @GetMapping("/{id}")
    @RequiresAction("hrms.hiring.interview")
    public ApiResponse<InterviewResponse> getInterview(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        InterviewResponse interview = interviewService.getInterview(tenantId, id);
        return ApiResponse.success("Interview retrieved", interview);
    }

    @PostMapping("/{id}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("hrms.hiring.interview")
    public ApiResponse<InterviewFeedbackResponse> submitFeedback(
            @PathVariable("id") UUID id, @Valid @RequestBody InterviewFeedbackRequest request) {
        UUID tenantId = TenantContext.require();
        UUID interviewerId = null;
        if (employeeService != null) {
            EmployeeResponse emp = employeeService.currentEmployee().orElse(null);
            if (emp != null) {
                interviewerId = emp.id();
            }
        }
        if (interviewerId == null) {
            // fallback to the assigned interviewer on the interview
            InterviewResponse interview = interviewService.getInterview(tenantId, id);
            interviewerId = interview.interviewerId();
        }

        InterviewFeedbackResponse feedback = interviewService.submitFeedback(tenantId, id, interviewerId, request);
        return ApiResponse.success("Interview feedback submitted successfully", feedback);
    }

    @GetMapping("/{id}/feedback")
    @RequiresAction("hrms.hiring.read")
    public ApiResponse<List<InterviewFeedbackResponse>> listFeedback(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        List<InterviewFeedbackResponse> list = interviewService.listFeedback(tenantId, id);
        return ApiResponse.success("Feedback list retrieved", list);
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
