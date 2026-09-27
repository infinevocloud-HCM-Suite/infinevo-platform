package com.infinevo.core.lop;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing loss-of-pay policies and resolving working-day basis (W-18.1).
 */
@RestController
@RequestMapping("/api/v1/lop-policy")
public class LopPolicyController {

    private final LopPolicyService policyService;
    private final WorkingDayBasisCalculator basisCalculator;

    public LopPolicyController(LopPolicyService policyService, WorkingDayBasisCalculator basisCalculator) {
        this.policyService = Objects.requireNonNull(policyService, "policyService must not be null");
        this.basisCalculator = Objects.requireNonNull(basisCalculator, "basisCalculator must not be null");
    }

    /**
     * Resolves the policy in force as of the given date (defaults to today).
     */
    @RequiresAction("core.lop_policy.read")
    @GetMapping
    public LopPolicyResponse getPolicyInForce(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return policyService.getPolicyInForce(asOf);
    }

    /**
     * Creates or updates a policy version with effectiveFrom.
     */
    @RequiresAction("core.lop_policy.manage")
    @PutMapping
    public LopPolicyResponse savePolicy(@RequestBody LopPolicyRequest request) {
        return policyService.savePolicy(request);
    }

    /**
     * Resolves the working-day basis for a pay period and optional employee.
     * Returns 409 when no policy is in force.
     */
    @RequiresAction("core.lop_policy.read")
    @GetMapping("/basis")
    public WorkingDayBasisResponse getBasis(
            @RequestParam String period, @RequestParam(required = false) UUID employeeId) {
        YearMonth ym = parseYearMonth(period);
        return basisCalculator.basisFor(ym, employeeId);
    }

    private YearMonth parseYearMonth(String period) {
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("period is required and must not be blank");
        }
        String trimmed = period.trim();
        try {
            if (trimmed.length() == 7) {
                return YearMonth.parse(trimmed);
            }
            return YearMonth.from(LocalDate.parse(trimmed));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid period format '" + period + "'. Expected YYYY-MM (e.g. 2026-07)", e);
        }
    }

    @ExceptionHandler(NoLopPolicyException.class)
    public ResponseEntity<ApiErrorResponse> handleNoLopPolicy(NoLopPolicyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED, "Malformed JSON request body: " + e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
