package com.infinevo.core.wfh;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/wfh-imports")
public class WfhImportController {

    private final WfhImportService importService;
    private final WfhImportLogRepository importLogRepository;

    public WfhImportController(WfhImportService importService, WfhImportLogRepository importLogRepository) {
        this.importService = Objects.requireNonNull(importService, "importService must not be null");
        this.importLogRepository = Objects.requireNonNull(importLogRepository, "importLogRepository must not be null");
    }

    @GetMapping
    @RequiresAction("core.wfh_balance.manage")
    public java.util.List<WfhImportLog> listImports() {
        UUID tenantId = TenantContext.require();
        return importService.listImports(tenantId);
    }

    @GetMapping("/template")
    @RequiresAction("core.wfh_balance.manage")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] csv = importService.generateSampleCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wfh_allocations_template.csv\"")
                .body(csv);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresAction("core.wfh_balance.manage")
    public WfhImportSummaryResponse importFromMultipart(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "wfhYear", required = false) String wfhYear,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun)
            throws IOException {
        UUID tenantId = TenantContext.require();
        return importService.importAllocations(tenantId, file.getOriginalFilename(), wfhYear, file.getBytes(), dryRun);
    }

    public record JsonImportRequest(String fileName, String wfhYear, String csvContent, boolean dryRun) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresAction("core.wfh_balance.manage")
    public WfhImportSummaryResponse importFromJson(@RequestBody JsonImportRequest request) {
        UUID tenantId = TenantContext.require();
        byte[] bytes =
                request.csvContent() != null ? request.csvContent().getBytes(StandardCharsets.UTF_8) : new byte[0];
        return importService.importAllocations(
                tenantId, request.fileName(), request.wfhYear(), bytes, request.dryRun());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
