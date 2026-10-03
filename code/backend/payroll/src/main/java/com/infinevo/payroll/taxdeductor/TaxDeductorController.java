package com.infinevo.payroll.taxdeductor;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing employer tax deductor details (W-36.3).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/settings/tax-deductor")
public class TaxDeductorController {

    private final TaxDeductorService taxDeductorService;

    public TaxDeductorController(TaxDeductorService taxDeductorService) {
        this.taxDeductorService = Objects.requireNonNull(taxDeductorService, "taxDeductorService must not be null");
    }

    @PutMapping
    @RequiresAction("payroll.settings.manage")
    public ResponseEntity<TaxDeductorApiResponse<TaxDeductorResponse>> put(@RequestBody TaxDeductorRequest request) {
        TaxDeductorResponse response = taxDeductorService.save(request);
        return ResponseEntity.ok(TaxDeductorApiResponse.ok("Tax deductor details saved successfully", response));
    }

    @GetMapping
    @RequiresAction(
            value = "payroll.settings.manage",
            anyOf = {"payroll.statutory_report.read"})
    public ResponseEntity<TaxDeductorApiResponse<TaxDeductorResponse>> get() {
        TaxDeductorResponse response = taxDeductorService
                .current()
                .orElseThrow(() -> new TaxDeductorNotFoundException("Tax deductor details not configured for tenant"));
        return ResponseEntity.ok(TaxDeductorApiResponse.ok("Tax deductor details retrieved successfully", response));
    }

    @ExceptionHandler(TaxDeductorValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(TaxDeductorValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(TaxDeductorNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(TaxDeductorNotFoundException ex) {
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
