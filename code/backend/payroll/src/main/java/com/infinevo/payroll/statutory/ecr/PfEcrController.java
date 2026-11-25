package com.infinevo.payroll.statutory.ecr;

import com.infinevo.payroll.payrun.PayFigureNotComputedException;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
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
 * REST controller exposing monthly EPFO ECR 2.0 returns preview and file exports (W-87).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/payruns/{payrunId}/reports/pf-ecr")
public class PfEcrController {

    private static final Logger log = LoggerFactory.getLogger(PfEcrController.class);

    private final PfEcrService pfEcrService;

    public PfEcrController(PfEcrService pfEcrService) {
        this.pfEcrService = Objects.requireNonNull(pfEcrService, "pfEcrService must not be null");
    }

    /**
     * Retrieves preview and pre-filing validation warnings for a regular pay run.
     */
    @GetMapping("/preview")
    @RequiresAction("payroll.statutory_report.read")
    public ApiResponse<PfEcrPreviewResponse> getPreview(@PathVariable("payrunId") UUID payrunId) {
        log.info("Generating PF ECR preview for pay run {}", payrunId);
        PfEcrPreviewResponse preview = pfEcrService.getEcrPreview(payrunId);
        return ApiResponse.ok("PF ECR preview generated successfully", preview);
    }

    /**
     * Exports EPFO ECR 2.0 text file, Excel audit workbook, or CSV.
     */
    @GetMapping("/export")
    @RequiresAction("payroll.statutory_report.export")
    public ResponseEntity<byte[]> exportEcr(
            @PathVariable("payrunId") UUID payrunId,
            @RequestParam(name = "format", defaultValue = "TXT") String format) {
        PfEcrFormat ecrFormat = PfEcrFormat.parse(format);
        log.info("Exporting PF ECR file for pay run {} in format {}", payrunId, ecrFormat);

        PfEcrFile file = pfEcrService.exportEcrFile(payrunId, ecrFormat);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(file.mediaType())
                .body(file.content());
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
