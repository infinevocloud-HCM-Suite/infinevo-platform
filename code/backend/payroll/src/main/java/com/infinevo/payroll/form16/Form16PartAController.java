package com.infinevo.payroll.form16;

import com.infinevo.core.document.DocumentService;
import com.infinevo.payroll.form16.exception.PartANotFoundException;
import com.infinevo.payroll.form16.exception.PartATooLargeException;
import com.infinevo.payroll.form16.exception.PartAZipException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Form 16 Part A — the officer's TRACES ZIP upload and the two reads (W-36.5 §4).
 *
 * <p>Thin by rule: unpack, delegate, repack. No endpoint names a tenant; {@code TenantContextFilter}
 * bound it from the verified token. The handlers are local, for the reason {@code DocumentController}
 * gives: there is no global advice.
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class Form16PartAController {

    private final Form16PartAService service;

    public Form16PartAController(Form16PartAService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @PostMapping(path = "/api/v1/payroll/form16/{fy}/part-a", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresAction("payroll.statutory_report.generate")
    public ResponseEntity<PartAResponse<PartAUploadResult>> upload(
            @PathVariable("fy") String financialYear, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(PartAResponse.ok("Form 16 Part A processed", service.upload(financialYear, file)));
    }

    @GetMapping("/api/v1/payroll/form16/{fy}/part-a")
    @RequiresAction("payroll.statutory_report.read")
    public ResponseEntity<PartAResponse<List<PartARow>>> list(@PathVariable("fy") String financialYear) {
        return ResponseEntity.ok(
                PartAResponse.ok("Form 16 Part A certificates retrieved", service.list(financialYear)));
    }

    @GetMapping("/api/v1/me/form16/{fy}/part-a")
    @RequiresAction("payroll.payslip.read_own")
    public ResponseEntity<PartAResponse<PartAOwn>> own(@PathVariable("fy") String financialYear) {
        return ResponseEntity.ok(PartAResponse.ok("Form 16 Part A retrieved", service.own(financialYear)));
    }

    /** Not a ZIP, password-protected, or over the entry limits — the code says which. */
    @ExceptionHandler(PartAZipException.class)
    public ResponseEntity<ApiErrorResponse> handleZip(PartAZipException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(e.code(), e.getMessage(), Map.of(), traceId(), Instant.now()));
    }

    @ExceptionHandler(PartATooLargeException.class)
    public ResponseEntity<ApiErrorResponse> handleTooLarge(PartATooLargeException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipartTooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ApiError.VALIDATION_FAILED, "The file is too large");
    }

    @ExceptionHandler({PartANotFoundException.class, DocumentService.NotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, "No Form 16 Part A on file");
    }

    /** A financial year that is not {@code YYYY-YYYY}. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(Exception e) {
        return error(
                HttpStatus.BAD_REQUEST,
                ApiError.VALIDATION_FAILED,
                "The request could not be read. Send multipart/form-data with a 'file' part holding the ZIP.");
    }

    @ExceptionHandler(DocumentService.StorageUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleStorageUnavailable(DocumentService.StorageUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ApiError.INTERNAL, e.getMessage());
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
