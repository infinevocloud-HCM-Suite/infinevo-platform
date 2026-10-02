package com.infinevo.hrms.attendance;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for attendance clock-in and clock-out operations (W-40.3 §4).
 */
@RestController
@RequestMapping("/api/v1/hrms/attendance")
@RequiresModule(PlatformModule.HRMS)
public class ClockController {

    private final ClockService clockService;

    public ClockController(ClockService clockService) {
        this.clockService = Objects.requireNonNull(clockService, "clockService must not be null");
    }

    @PostMapping("/clock-in")
    @RequiresAction("hrms.attendance.mark")
    public ResponseEntity<ClockSessionResponse> clockIn() {
        return ResponseEntity.status(HttpStatus.CREATED).body(clockService.clockIn());
    }

    @PostMapping("/clock-out")
    @RequiresAction("hrms.attendance.mark")
    public ResponseEntity<ClockOutResponse> clockOut() {
        return ResponseEntity.ok(clockService.clockOut());
    }

    @GetMapping("/today")
    @RequiresAction("hrms.attendance.mark")
    public ResponseEntity<TodayResponse> today() {
        return ResponseEntity.ok(clockService.today());
    }

    @GetMapping("/sessions/mine")
    @RequiresAction("core.attendance.read_own")
    public ResponseEntity<List<ClockSessionResponse>> mySessions(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(clockService.mySessions(from, to));
    }

    @GetMapping("/sessions")
    @RequiresAction("core.attendance.read")
    public ResponseEntity<List<ClockSessionResponse>> allSessions(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "employeeId", required = false) UUID employeeId) {
        return ResponseEntity.ok(clockService.allSessions(from, to, employeeId));
    }

    @ExceptionHandler(ClockService.ClockConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ClockService.ClockConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(ClockService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ClockService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
