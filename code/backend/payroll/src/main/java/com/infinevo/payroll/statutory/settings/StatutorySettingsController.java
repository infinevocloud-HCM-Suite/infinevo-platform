package com.infinevo.payroll.statutory.settings;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
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
 * REST controller for provident fund and state insurance statutory settings (W-31.1).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/settings")
public class StatutorySettingsController {

    private final StatutorySettingsService statutorySettingsService;

    public StatutorySettingsController(StatutorySettingsService statutorySettingsService) {
        this.statutorySettingsService =
                Objects.requireNonNull(statutorySettingsService, "statutorySettingsService must not be null");
    }

    @GetMapping("/epf")
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<StatutoryApiResponse<EpfSettingResponse>> getEpf() {
        UUID tenantId = TenantContext.require();
        return ResponseEntity.ok(
                StatutoryApiResponse.ok("EPF settings retrieved successfully", statutorySettingsService.epf(tenantId)));
    }

    @PutMapping("/epf")
    @RequiresAction("payroll.settings.manage")
    public ResponseEntity<StatutoryApiResponse<EpfSettingResponse>> putEpf(@RequestBody EpfSettingRequest request) {
        return ResponseEntity.ok(
                StatutoryApiResponse.ok("EPF settings saved successfully", statutorySettingsService.saveEpf(request)));
    }

    @GetMapping("/esi")
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<StatutoryApiResponse<EsiSettingResponse>> getEsi() {
        UUID tenantId = TenantContext.require();
        return ResponseEntity.ok(
                StatutoryApiResponse.ok("ESI settings retrieved successfully", statutorySettingsService.esi(tenantId)));
    }

    @PutMapping("/esi")
    @RequiresAction("payroll.settings.manage")
    public ResponseEntity<StatutoryApiResponse<EsiSettingResponse>> putEsi(@RequestBody EsiSettingRequest request) {
        return ResponseEntity.ok(
                StatutoryApiResponse.ok("ESI settings saved successfully", statutorySettingsService.saveEsi(request)));
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
