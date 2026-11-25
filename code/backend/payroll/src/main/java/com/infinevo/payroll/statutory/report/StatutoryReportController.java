package com.infinevo.payroll.statutory.report;

import com.infinevo.core.report.ExportFormat;
import com.infinevo.payroll.payrun.PayFigureNotComputedException;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing monthly ESI and state-wise PT statutory return exports (W-95).
 * Guarded by {@code payroll.statutory_report.read}.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/payruns/{id}/reports")
public class StatutoryReportController {

    private static final Logger log = LoggerFactory.getLogger(StatutoryReportController.class);

    private final StatutoryReportService statutoryReportService;

    public StatutoryReportController(StatutoryReportService statutoryReportService) {
        this.statutoryReportService =
                Objects.requireNonNull(statutoryReportService, "statutoryReportService must not be null");
    }

    /**
     * Downloads monthly ESI Return for a computed pay run in XLSX or CSV format.
     */
    @GetMapping("/esi")
    @RequiresAction("payroll.statutory_report.read")
    public ResponseEntity<byte[]> getEsiReturn(
            @PathVariable("id") UUID id, @RequestParam(name = "format", defaultValue = "XLSX") String format) {
        ExportFormat exportFormat = parseFormat(format);
        log.info("Generating ESI Return for pay run {} in format {}", id, exportFormat);

        StatutoryReportFile reportFile = statutoryReportService.generateEsiReturnFile(id, exportFormat);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + reportFile.filename() + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(reportFile.mediaType())
                .body(reportFile.content());
    }

    /**
     * Downloads state-wise Professional Tax Return statement for a computed pay run in XLSX or CSV format,
     * optionally filtered to a single state.
     */
    @GetMapping("/pt")
    @RequiresAction("payroll.statutory_report.read")
    public ResponseEntity<byte[]> getPtReturn(
            @PathVariable("id") UUID id,
            @RequestParam(name = "format", defaultValue = "XLSX") String format,
            @RequestParam(name = "state", required = false) String state) {
        ExportFormat exportFormat = parseFormat(format);
        log.info("Generating PT Return for pay run {} in format {}, state {}", id, exportFormat, state);

        StatutoryReportFile reportFile = statutoryReportService.generatePtReturnFile(id, exportFormat, state);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + reportFile.filename() + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(reportFile.mediaType())
                .body(reportFile.content());
    }

    private static ExportFormat parseFormat(String format) {
        if (format == null || format.isBlank()) {
            return ExportFormat.XLSX;
        }
        try {
            return ExportFormat.valueOf(format.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unsupported export format: " + format + ". Supported formats are XLSX, CSV");
        }
    }

    @ExceptionHandler(PayRunNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PayRunNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PayFigureNotComputedException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(PayFigureNotComputedException ex) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, ex.getMessage());
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
