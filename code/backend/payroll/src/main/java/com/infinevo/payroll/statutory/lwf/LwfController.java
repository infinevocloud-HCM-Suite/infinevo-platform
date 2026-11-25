package com.infinevo.payroll.statutory.lwf;

import com.infinevo.payroll.payrun.PayFigureNotComputedException;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Labour Welfare Fund (LWF) configuration and statutory return export (W-92).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping
public class LwfController {

    private static final Logger log = LoggerFactory.getLogger(LwfController.class);

    private static final MediaType MEDIA_TYPE_XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType MEDIA_TYPE_CSV = MediaType.parseMediaType("text/csv; charset=utf-8");

    private final LwfService lwfService;

    public LwfController(LwfService lwfService) {
        this.lwfService = Objects.requireNonNull(lwfService, "lwfService must not be null");
    }

    /**
     * Lists LWF settings for all states where the tenant operates active work locations.
     */
    @GetMapping("/api/v1/payroll/settings/lwf")
    @RequiresAction("payroll.lwf.read")
    public ApiResponse<List<LwfStateSettingResponse>> listSettings() {
        UUID tenantId = TenantContext.require();
        log.info("Listing LWF settings for tenant {}", tenantId);
        List<LwfStateSettingResponse> settings = lwfService.listSettings(tenantId);
        return ApiResponse.ok("LWF settings retrieved", settings);
    }

    /**
     * Retrieves the LWF setting for a specific state.
     */
    @GetMapping("/api/v1/payroll/settings/lwf/{stateCode}")
    @RequiresAction("payroll.lwf.read")
    public ApiResponse<LwfStateSettingResponse> getStateSetting(@PathVariable("stateCode") String stateCode) {
        UUID tenantId = TenantContext.require();
        log.info("Getting LWF setting for tenant {} and state {}", tenantId, stateCode);
        LwfStateSettingResponse response = lwfService.getStateSetting(tenantId, stateCode);
        return ApiResponse.ok("LWF state setting retrieved", response);
    }

    /**
     * Updates tenant LWF registration and active/inactive toggle for a state.
     */
    @PutMapping("/api/v1/payroll/settings/lwf/{stateCode}")
    @RequiresAction("payroll.lwf.manage")
    public ApiResponse<LwfStateSettingResponse> updateSetting(
            @PathVariable("stateCode") String stateCode, @RequestBody LwfSettingUpdateRequest request) {
        UUID tenantId = TenantContext.require();
        log.info("Updating LWF setting for tenant {} and state {}: {}", tenantId, stateCode, request);
        LwfStateSettingResponse updated = lwfService.updateSetting(tenantId, stateCode, request, "admin");
        return ApiResponse.ok("LWF setting updated successfully", updated);
    }

    /**
     * Exports the periodic LWF return statement for a regular pay run.
     */
    @GetMapping({
        "/api/v1/payroll/payruns/{payrunId}/statutory/lwf/export",
        "/api/v1/payroll/payruns/{payrunId}/reports/lwf"
    })
    @RequiresAction("payroll.lwf.read")
    public ResponseEntity<byte[]> exportReturn(
            @PathVariable("payrunId") UUID payrunId,
            @RequestParam(name = "format", defaultValue = "XLSX") String format) {
        UUID tenantId = TenantContext.require();
        log.info("Exporting LWF return statement for pay run {} in format {}", payrunId, format);

        byte[] content = lwfService.exportReturnFile(tenantId, payrunId, format);
        boolean isCsv = "CSV".equalsIgnoreCase(format);
        String ext = isCsv ? "csv" : "xlsx";
        String filename = "lwf-return-" + payrunId + "." + ext;
        MediaType mediaType = isCsv ? MEDIA_TYPE_CSV : MEDIA_TYPE_XLSX;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(mediaType)
                .body(content);
    }

    @ExceptionHandler(LwfNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(LwfNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PayRunNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePayRunNotFound(PayRunNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PayFigureNotComputedException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(PayFigureNotComputedException ex) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(LwfValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(LwfValidationException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, ex.getMessage());
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
