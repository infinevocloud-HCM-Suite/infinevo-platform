package com.infinevo.payroll.compliance.form24q;

import com.infinevo.payroll.form16.exception.DeductorNotSetException;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Form 24Q Quarterly compliance operations (W-89.2).
 */
@RestController
@RequestMapping("/api/v1/payroll/compliance/24q")
@RequiresModule(PlatformModule.PAYROLL)
public class Form24QController {

    private final Form24QAnnexure1Service annexure1Service;

    public Form24QController(Form24QAnnexure1Service annexure1Service) {
        this.annexure1Service = Objects.requireNonNull(annexure1Service, "annexure1Service must not be null");
    }

    @GetMapping("/{fy}/{quarter}/annexure-1")
    @RequiresAction("payroll.tax.view")
    public ResponseEntity<ApiResponse<Annexure1PreviewResponse>> previewAnnexure1(
            @PathVariable("fy") String financialYear, @PathVariable("quarter") String quarter) {
        Annexure1PreviewResponse response = annexure1Service.previewAnnexure1(financialYear, quarter);
        return ResponseEntity.ok(ApiResponse.ok("Form 24Q Annexure I preview generated successfully", response));
    }

    @GetMapping("/{fy}/{quarter}/annexure-1/export")
    @RequiresAction("payroll.tax.view")
    public ResponseEntity<byte[]> exportAnnexure1(
            @PathVariable("fy") String financialYear,
            @PathVariable("quarter") String quarter,
            @RequestParam(name = "format", defaultValue = "CSV") String format) {
        byte[] content = annexure1Service.exportAnnexure1(financialYear, quarter, format);

        String normalizedFormat = format.trim().toUpperCase();
        String extension = "TXT".equals(normalizedFormat) ? "txt" : "csv";
        MediaType mediaType = "TXT".equals(normalizedFormat)
                ? MediaType.parseMediaType("text/plain; charset=UTF-8")
                : MediaType.parseMediaType("text/csv; charset=UTF-8");

        String filename = String.format("24Q_%s_%s_Annexure1.%s", financialYear, quarter.toUpperCase(), extension);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @ExceptionHandler(Form24QValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(Form24QValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(DeductorNotSetException.class)
    public ResponseEntity<ApiErrorResponse> handleDeductorNotSet(DeductorNotSetException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, ex.getMessage(), getTraceId()));
    }

    private String getTraceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
