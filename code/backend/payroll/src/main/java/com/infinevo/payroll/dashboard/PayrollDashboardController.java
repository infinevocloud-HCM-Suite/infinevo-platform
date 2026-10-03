package com.infinevo.payroll.dashboard;

import com.infinevo.payroll.payrun.PayRunApiResponse;
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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * W-37 §4 — one read-only {@code GET} for the whole payroll dashboard. Replaces the three unused
 * legacy calls ({@code DashboardController.java:25-116}) with one round trip under {@code /api/v1}
 * and the {@code status} / {@code message} / {@code data} envelope (DEBT-007, DEBT-008). The tenant
 * is the bound one; nothing here reads it from a header.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/dashboard")
public class PayrollDashboardController {

    private final PayrollDashboardService dashboardService;

    public PayrollDashboardController(PayrollDashboardService dashboardService) {
        this.dashboardService = Objects.requireNonNull(dashboardService, "dashboardService must not be null");
    }

    @GetMapping
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<PayrollDashboardResponse>> summary(
            @RequestParam(name = "fy", required = false) Integer fy) {
        return ResponseEntity.ok(
                PayRunApiResponse.ok("Payroll dashboard retrieved successfully", dashboardService.summary(fy)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return badRequest(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequest(ex.getName() + " must be a year, as YYYY");
    }

    private static ResponseEntity<ApiErrorResponse> badRequest(String message) {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        message,
                        traceId != null ? traceId : UUID.randomUUID().toString()));
    }
}
