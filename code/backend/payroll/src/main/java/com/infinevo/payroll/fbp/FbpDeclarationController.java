package com.infinevo.payroll.fbp;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.salary.SalaryConflictException;
import com.infinevo.payroll.salary.SalaryNotFoundException;
import com.infinevo.payroll.salary.SalaryValidationException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for employee Flexible Benefit Plan declarations (W-27.2).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping
public class FbpDeclarationController {

    private final FbpDeclarationService fbpDeclarationService;

    public FbpDeclarationController(FbpDeclarationService fbpDeclarationService) {
        this.fbpDeclarationService =
                Objects.requireNonNull(fbpDeclarationService, "fbpDeclarationService must not be null");
    }

    @GetMapping("/api/v1/me/fbp-declaration")
    @RequiresAction("payroll.fbp.read_own")
    public FbpDeclarationResponse readOwn() {
        return fbpDeclarationService.readOwn();
    }

    @PutMapping("/api/v1/me/fbp-declaration")
    @RequiresAction("payroll.fbp.declare_own")
    public FbpDeclarationResponse declareOwn(@RequestBody FbpDeclarationRequest request) {
        return fbpDeclarationService.declareOwn(request);
    }

    @GetMapping("/api/v1/payroll/employees/{employeeId}/fbp-declaration")
    @RequiresAction("payroll.fbp.read")
    public FbpDeclarationResponse read(
            @PathVariable("employeeId") UUID employeeId,
            @RequestParam(value = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        return fbpDeclarationService.read(employeeId, asOf);
    }

    @PutMapping("/api/v1/payroll/employees/{employeeId}/fbp-declaration")
    @RequiresAction("payroll.salary.manage")
    public FbpDeclarationResponse set(
            @PathVariable("employeeId") UUID employeeId, @RequestBody FbpDeclarationRequest request) {
        return fbpDeclarationService.set(employeeId, request);
    }

    @ExceptionHandler(SalaryConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(SalaryConflictException e) {
        String code = e.getMessage() != null && e.getMessage().contains("WINDOW_CLOSED")
                ? "WINDOW_CLOSED"
                : ApiError.CONFLICT.code();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(code, e.getMessage(), Map.of(), traceId(), java.time.Instant.now()));
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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(ApiError.FORBIDDEN, e.getMessage(), traceId()));
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
