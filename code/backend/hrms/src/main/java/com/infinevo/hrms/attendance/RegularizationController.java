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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Attendance regularization requests (W-40.4 §4). Approving and rejecting go through {@code core}'s
 * {@code POST /api/v1/approvals/steps/{stepId}/decide}; nothing here decides.
 */
@RestController
@RequestMapping("/api/v1/hrms/attendance/regularizations")
@RequiresModule(PlatformModule.HRMS)
public class RegularizationController {

    private final RegularizationService regularizationService;

    public RegularizationController(RegularizationService regularizationService) {
        this.regularizationService =
                Objects.requireNonNull(regularizationService, "regularizationService must not be null");
    }

    @PostMapping
    @RequiresAction("hrms.attendance.mark")
    public ResponseEntity<RegularizationResponse> submit(@RequestBody RegularizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(regularizationService.submit(request));
    }

    @GetMapping("/mine")
    @RequiresAction("core.attendance.read_own")
    public ResponseEntity<List<RegularizationResponse>> mine(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(regularizationService.mine(from, to));
    }

    @GetMapping
    @RequiresAction("core.attendance.read")
    public ResponseEntity<List<RegularizationResponse>> all(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "status", required = false) RegularizationStatus status,
            @RequestParam(value = "employeeId", required = false) UUID employeeId) {
        return ResponseEntity.ok(regularizationService.all(from, to, status, employeeId));
    }

    @ExceptionHandler(RegularizationService.ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(RegularizationService.ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(RegularizationService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(RegularizationService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, "Request body is not readable", traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
