package com.infinevo.payroll.schedule;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
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
 * REST controller for the tenant pay schedule (W-28 §4).
 *
 * <p>GET /api/v1/payroll/pay-schedule — returns schedule or default empty model with {@code exists: false}; never writes.
 * PUT /api/v1/payroll/pay-schedule — upsert row, 200.
 * GET /api/v1/payroll/pay-schedule/period?period=YYYY-MM — returns PayPeriodResponse; 409 if no schedule; 400 if period before first period.
 */
@RequiresModule(PlatformModule.PAYROLL)
@RestController
@RequestMapping("/api/v1/payroll/pay-schedule")
public class PayScheduleController {

    private final PayScheduleService scheduleService;
    private final PayPeriodService periodService;

    public PayScheduleController(PayScheduleService scheduleService, PayPeriodService periodService) {
        this.scheduleService = Objects.requireNonNull(scheduleService, "scheduleService must not be null");
        this.periodService = Objects.requireNonNull(periodService, "periodService must not be null");
    }

    @RequiresAction("payroll.structure.read")
    @GetMapping
    public PayScheduleResponse get() {
        return scheduleService.get();
    }

    @RequiresAction("payroll.settings.manage")
    @PutMapping
    public PayScheduleResponse upsert(@RequestBody PayScheduleRequest request) {
        return scheduleService.upsert(request);
    }

    @RequiresAction("payroll.structure.read")
    @GetMapping("/period")
    public PayPeriodResponse period(@RequestParam String period) {
        YearMonth ym = parseYearMonth(period);
        return periodService.periodFor(ym);
    }

    // ── exception handlers ────────────────────────────────────────────────────

    @ExceptionHandler(NoPayScheduleException.class)
    public ResponseEntity<ApiErrorResponse> handleNoPaySchedule(NoPayScheduleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED, "Malformed JSON request body: " + e.getMessage(), traceId()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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

    private static String traceId() {
        String id = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return id == null || id.isBlank() ? UUID.randomUUID().toString().substring(0, 8) : id;
    }
}
