package com.infinevo.payroll.tds;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for annual TDS records and self-service tax visibility (W-36.1 §4).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class EmployeeTdsController {

    private final EmployeeTdsService employeeTdsService;

    public EmployeeTdsController(EmployeeTdsService employeeTdsService) {
        this.employeeTdsService = Objects.requireNonNull(employeeTdsService, "employeeTdsService must not be null");
    }

    @PutMapping("/api/v1/payroll/employees/{employeeId}/tds/{fy}")
    @RequiresAction("payroll.tax_declaration.verify")
    public ResponseEntity<EmployeeTdsApiResponse<EmployeeTdsResponse>> record(
            @PathVariable("employeeId") UUID employeeId,
            @PathVariable("fy") String financialYear,
            @RequestBody RecordTdsRequest request) {
        TdsFigures figures = request != null ? request.toFigures() : null;
        EmployeeTdsResponse response = employeeTdsService.record(employeeId, financialYear, figures, TdsSource.OFFICER);
        return ResponseEntity.ok(EmployeeTdsApiResponse.ok("TDS record saved successfully", response));
    }

    @GetMapping("/api/v1/payroll/employees/{employeeId}/tds/{fy}")
    @RequiresAction("payroll.tax_declaration.read")
    public ResponseEntity<EmployeeTdsApiResponse<EmployeeTdsResponse>> active(
            @PathVariable("employeeId") UUID employeeId, @PathVariable("fy") String financialYear) {
        EmployeeTdsResponse response = employeeTdsService
                .active(employeeId, financialYear)
                .orElseThrow(() -> new EmployeeTdsNotFoundException(
                        "No active TDS record found for employee in financial year " + financialYear));
        return ResponseEntity.ok(EmployeeTdsApiResponse.ok("Active TDS record retrieved successfully", response));
    }

    @GetMapping("/api/v1/payroll/employees/{employeeId}/tds/{fy}/history")
    @RequiresAction("payroll.tax_declaration.read")
    public ResponseEntity<EmployeeTdsApiResponse<List<EmployeeTdsResponse>>> history(
            @PathVariable("employeeId") UUID employeeId, @PathVariable("fy") String financialYear) {
        List<EmployeeTdsResponse> history = employeeTdsService.history(employeeId, financialYear);
        return ResponseEntity.ok(EmployeeTdsApiResponse.ok("TDS history retrieved successfully", history));
    }

    @GetMapping("/api/v1/me/tds/{fy}")
    @RequiresAction("payroll.tax_declaration.read_own")
    public ResponseEntity<EmployeeTdsApiResponse<EmployeeTdsResponse>> me(@PathVariable("fy") String financialYear) {
        EmployeeTdsResponse response = employeeTdsService
                .activeOwn(financialYear)
                .orElseThrow(() -> new EmployeeTdsNotFoundException(
                        "No active TDS record found for current user in financial year " + financialYear));
        return ResponseEntity.ok(EmployeeTdsApiResponse.ok("TDS record retrieved successfully", response));
    }

    @ExceptionHandler(EmployeeTdsValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(EmployeeTdsValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler({EmployeeTdsNotFoundException.class, EmployeeService.NotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(ApiError.FORBIDDEN, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED, "Request body is unreadable or malformed", getTraceId()));
    }

    private String getTraceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
