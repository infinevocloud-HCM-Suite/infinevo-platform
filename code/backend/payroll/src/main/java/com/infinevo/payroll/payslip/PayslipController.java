package com.infinevo.payroll.payslip;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for authenticated payslip reads (W-36.2 §4).
 * Officer reads for any computed run, employee reads for own paid runs.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class PayslipController {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 100;

    private final PayslipService payslipService;

    public PayslipController(PayslipService payslipService) {
        this.payslipService = Objects.requireNonNull(payslipService, "payslipService must not be null");
    }

    @GetMapping("/api/v1/payroll/payruns/{id}/employees/{employeeId}/payslip")
    @RequiresAction("payroll.payslip.read")
    public ResponseEntity<PayslipApiResponse<PayslipResponse>> forOfficer(
            @PathVariable("id") UUID id, @PathVariable("employeeId") UUID employeeId) {
        return ResponseEntity.ok(
                PayslipApiResponse.ok("Payslip retrieved successfully", payslipService.forOfficer(id, employeeId)));
    }

    @GetMapping("/api/v1/me/payslips")
    @RequiresAction("payroll.payslip.read_own")
    public ResponseEntity<PayslipApiResponse<Page<PayslipSummaryResponse>>> listOwn(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), clampSize(size));
        return ResponseEntity.ok(
                PayslipApiResponse.ok("Payslips retrieved successfully", payslipService.listOwn(pageable)));
    }

    @GetMapping("/api/v1/me/payslips/{payrunId}")
    @RequiresAction("payroll.payslip.read_own")
    public ResponseEntity<PayslipApiResponse<PayslipResponse>> own(@PathVariable("payrunId") UUID payrunId) {
        return ResponseEntity.ok(PayslipApiResponse.ok("Payslip retrieved successfully", payslipService.own(payrunId)));
    }

    @ExceptionHandler(PayslipNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PayslipNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PayslipConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(PayslipConflictException ex) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PayslipForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(PayslipForbiddenException ex) {
        return error(HttpStatus.FORBIDDEN, ApiError.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, ex.getMessage());
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(1, size), MAX_PAGE_SIZE);
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
