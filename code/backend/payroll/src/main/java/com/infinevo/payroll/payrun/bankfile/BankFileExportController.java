package com.infinevo.payroll.payrun.bankfile;

import com.infinevo.core.document.DocumentService;
import com.infinevo.payroll.payrun.PayRunNotFoundException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bank files made from a template for a pay run, and their history (W-88.2 spec section 4.4). */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/payruns/{id}/bank-files")
public class BankFileExportController {

    private final BankFileExportService service;

    public BankFileExportController(BankFileExportService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    @RequiresAction("payroll.bank_file.export")
    public BankFilePreviewResponse preview(@PathVariable("id") UUID id, @RequestBody BankFileExportRequest request) {
        return service.preview(id, request);
    }

    @PostMapping
    @RequiresAction("payroll.bank_file.export")
    public ResponseEntity<BankFileExportResponse> generate(
            @PathVariable("id") UUID id, @RequestBody BankFileExportRequest request) {
        BankFileExportResponse created = service.generate(id, request);
        return ResponseEntity.created(
                        URI.create("/api/v1/payroll/payruns/" + id + "/bank-files/" + created.id() + "/download"))
                .body(created);
    }

    @GetMapping
    @RequiresAction("payroll.bank_file.export")
    public List<BankFileExportResponse> list(@PathVariable("id") UUID id) {
        return service.list(id);
    }

    @GetMapping("/{exportId}/download")
    @RequiresAction("payroll.bank_file.export")
    public ResponseEntity<byte[]> download(@PathVariable("id") UUID id, @PathVariable("exportId") UUID exportId)
            throws IOException {
        BankFileExportService.Download download = service.download(id, exportId);
        byte[] bytes;
        try (InputStream in = download.content().content()) {
            bytes = in.readAllBytes();
        }
        String contentType = download.content().metadata().contentType();
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.export().fileName() + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(
                        contentType == null || contentType.isBlank()
                                ? MediaType.APPLICATION_OCTET_STREAM
                                : MediaType.parseMediaType(contentType))
                .body(bytes);
    }

    @ExceptionHandler(PayRunNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRunNotFound(PayRunNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BankFileInvalidStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleStatus(BankFileInvalidStatusException e) {
        return error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(DocumentService.StorageUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleStorage(DocumentService.StorageUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ApiError.INTERNAL, "The file store is unavailable; try again");
    }

    @ExceptionHandler(BankFileTemplateException.class)
    public ResponseEntity<ApiErrorResponse> handleTemplate(BankFileTemplateException e) {
        return e.kind() == BankFileTemplateException.Kind.NOT_FOUND
                ? error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, e.getMessage())
                : error(HttpStatus.CONFLICT, ApiError.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, "Request body is unreadable or malformed");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, traceId));
    }
}
