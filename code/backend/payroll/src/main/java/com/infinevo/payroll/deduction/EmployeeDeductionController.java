package com.infinevo.payroll.deduction;

import com.infinevo.core.payinput.PayInputService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-35.2 §4. Exactly five mappings: enter a batch, list, read one, reverse ({@code DELETE} is the verb —
 * the row stays, {@code REVERSED}), and the employee's own. No {@code PUT}: a deduction is never edited
 * (§13 decision 2), unlike legacy's update before {@code INPAYRUN} ({@code FEATURE_MAP.md:363}).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class EmployeeDeductionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final EmployeeDeductionService deductionService;

    public EmployeeDeductionController(EmployeeDeductionService deductionService) {
        this.deductionService = Objects.requireNonNull(deductionService, "deductionService must not be null");
    }

    @PostMapping("/api/v1/payroll/employee-deductions")
    @RequiresAction("payroll.employee_deduction.manage")
    public ResponseEntity<DeductionApiResponse<EmployeeDeductionBatchResponse>> enter(
            @RequestBody List<EmployeeDeductionLineRequest> lines) {
        EmployeeDeductionBatchResponse entered = deductionService.enter(lines);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DeductionApiResponse.created("Salary deductions entered successfully", entered));
    }

    @GetMapping("/api/v1/payroll/employee-deductions")
    @RequiresAction("payroll.employee_deduction.read")
    public ResponseEntity<DeductionApiResponse<Page<EmployeeDeductionResponse>>> list(
            @RequestParam(name = "employeeId", required = false) UUID employeeId,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "status", required = false) DeductionState status,
            @RequestParam(name = "deductionType", required = false) DeductionType deductionType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "period").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<EmployeeDeductionResponse> rows =
                deductionService.list(employeeId, parsePeriod(period), status, deductionType, pageable);
        return ResponseEntity.ok(DeductionApiResponse.ok("Salary deductions retrieved successfully", rows));
    }

    @GetMapping("/api/v1/payroll/employee-deductions/{id}")
    @RequiresAction("payroll.employee_deduction.read")
    public ResponseEntity<DeductionApiResponse<EmployeeDeductionResponse>> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(
                DeductionApiResponse.ok("Salary deduction retrieved successfully", deductionService.get(id)));
    }

    @DeleteMapping("/api/v1/payroll/employee-deductions/{id}")
    @RequiresAction("payroll.employee_deduction.manage")
    public ResponseEntity<DeductionApiResponse<EmployeeDeductionResponse>> reverse(
            @PathVariable("id") UUID id, @RequestParam(name = "reason") String reason) {
        return ResponseEntity.ok(DeductionApiResponse.ok(
                "Salary deduction reversed successfully", deductionService.reverse(id, reason)));
    }

    @GetMapping("/api/v1/me/employee-deductions")
    @RequiresAction("payroll.employee_deduction.read_own")
    public ResponseEntity<DeductionApiResponse<List<EmployeeDeductionResponse>>> listOwn() {
        return ResponseEntity.ok(
                DeductionApiResponse.ok("Salary deductions retrieved successfully", deductionService.listOwn()));
    }

    @ExceptionHandler(EmployeeDeductionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EmployeeDeductionNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({DeductionAlreadyReversedException.class, PayInputService.AlreadyReversedException.class})
    public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({
        EmployeeDeductionValidationException.class,
        PayInputService.ValidationException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<ApiErrorResponse> handleValidation(RuntimeException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return error(
                HttpStatus.BAD_REQUEST,
                ApiError.VALIDATION_FAILED,
                "Query parameter " + ex.getParameterName() + " is required");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, "Request body is unreadable or malformed");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, ApiError.FORBIDDEN, ex.getMessage());
    }

    private static YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(period.strip());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("period must be YYYY-MM, was " + period, e);
        }
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(
                        code,
                        message,
                        traceId != null ? traceId : UUID.randomUUID().toString()));
    }
}
