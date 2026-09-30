package com.infinevo.payroll.payrun;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * W-18.2 §4. Why a pay figure is what it is: the stored stamp beside the figure. Under the pay run
 * resource like the rest of W-29's endpoints. The guard admits {@code payroll.run.read} or
 * {@code payroll.payslip.read_own}; {@link PayFigureExplanationService} limits the second to the
 * caller's own row.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class PayRunExplainController {

    private final PayFigureExplanationService explanationService;

    public PayRunExplainController(PayFigureExplanationService explanationService) {
        this.explanationService = Objects.requireNonNull(explanationService, "explanationService must not be null");
    }

    @GetMapping("/api/v1/payroll/payruns/{id}/employees/{employeeId}/explain")
    @RequiresAction(value = PayFigureExplanationService.READ_ANY, anyOf = PayFigureExplanationService.READ_OWN)
    public ResponseEntity<PayRunApiResponse<PayFigureExplanationResponse>> explain(
            @PathVariable("id") UUID id, @PathVariable("employeeId") UUID employeeId) {
        return ResponseEntity.ok(
                PayRunApiResponse.ok("Pay figure explained successfully", explanationService.explain(id, employeeId)));
    }

    @ExceptionHandler(PayRunNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PayRunNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PayFigureNotComputedException.class)
    public ResponseEntity<ApiErrorResponse> handleNotComputed(PayFigureNotComputedException ex) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage());
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
