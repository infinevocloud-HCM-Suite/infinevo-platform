package com.infinevo.payroll.salary;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for employee statutory profile (W-26.2).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/employees/{employeeId}/statutory-profile")
public class EmployeeStatutoryProfileController {

    private final EmployeeStatutoryProfileService statutoryProfileService;

    public EmployeeStatutoryProfileController(EmployeeStatutoryProfileService statutoryProfileService) {
        this.statutoryProfileService =
                Objects.requireNonNull(statutoryProfileService, "statutoryProfileService must not be null");
    }

    @GetMapping
    @RequiresAction("payroll.salary.read")
    public StatutoryProfileResponse get(@PathVariable("employeeId") UUID employeeId) {
        return statutoryProfileService.get(employeeId);
    }

    @PutMapping
    @RequiresAction("payroll.salary.manage")
    public StatutoryProfileResponse upsert(
            @PathVariable("employeeId") UUID employeeId, @RequestBody StatutoryProfileRequest request) {
        return statutoryProfileService.upsert(employeeId, request);
    }

    @ExceptionHandler(SalaryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(SalaryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(EmployeeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEmployeeNotFound(EmployeeService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(SalaryValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(SalaryValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.getFieldErrors(), traceId()));
    }

    @ExceptionHandler(SalaryConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(SalaryConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}
