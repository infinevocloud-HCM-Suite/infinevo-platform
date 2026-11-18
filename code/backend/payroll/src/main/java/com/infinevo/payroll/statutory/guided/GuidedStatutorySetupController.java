package com.infinevo.payroll.statutory.guided;

import com.infinevo.payroll.statutory.settings.StatutoryApiResponse;
import com.infinevo.payroll.statutory.settings.StatutorySettingsValidationException;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for guided statutory setup wizard (W-93).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/settings/guided-setup")
public class GuidedStatutorySetupController {

    private final GuidedStatutorySetupService guidedStatutorySetupService;

    public GuidedStatutorySetupController(GuidedStatutorySetupService guidedStatutorySetupService) {
        this.guidedStatutorySetupService =
                Objects.requireNonNull(guidedStatutorySetupService, "guidedStatutorySetupService must not be null");
    }

    @GetMapping
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<StatutoryApiResponse<GuidedStatutoryReadinessResponse>> getReadiness() {
        GuidedStatutoryReadinessResponse response = guidedStatutorySetupService.getReadiness();
        return ResponseEntity.ok(StatutoryApiResponse.ok("Statutory readiness retrieved successfully", response));
    }

    @PostMapping
    @RequiresAction("payroll.settings.manage")
    public ResponseEntity<StatutoryApiResponse<GuidedStatutorySetupResponse>> applySetup(
            @RequestBody GuidedStatutorySetupRequest request) {
        GuidedStatutorySetupResponse response = guidedStatutorySetupService.applySetup(request);
        return ResponseEntity.ok(StatutoryApiResponse.ok("Guided statutory setup applied successfully", response));
    }

    @ExceptionHandler(StatutorySettingsValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(StatutorySettingsValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.getFieldErrors(), traceId()));
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
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}
