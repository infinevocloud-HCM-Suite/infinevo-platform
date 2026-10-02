package com.infinevo.hrms.attendance;

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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for tenant attendance preferences (W-40.1, spec section 4).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/hrms/attendance/preferences}: read preferences or system defaults</li>
 *   <li>{@code PUT /api/v1/hrms/attendance/preferences}: upsert preferences for tenant</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/hrms/attendance/preferences")
@RequiresModule(PlatformModule.HRMS)
public class AttendancePreferenceController {

    private final AttendancePreferenceService preferenceService;

    public AttendancePreferenceController(AttendancePreferenceService preferenceService) {
        this.preferenceService = Objects.requireNonNull(preferenceService, "preferenceService must not be null");
    }

    @GetMapping
    @RequiresAction("core.attendance.read")
    public AttendancePreferenceResponse getPreferences() {
        return preferenceService.current();
    }

    @PutMapping
    @RequiresAction("core.attendance.manage")
    public AttendancePreferenceResponse updatePreferences(@RequestBody AttendancePreferenceRequest request) {
        return preferenceService.save(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
