package com.infinevo.payroll.priorpayroll;

import com.infinevo.payroll.payrun.PayRunApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for prior payroll imports, history, months and status (W-38.1 §4).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class PriorPayrollController {

    private final PriorPayrollImportService importService;
    private final PriorPayrollService priorPayrollService;

    public PriorPayrollController(PriorPayrollImportService importService, PriorPayrollService priorPayrollService) {
        this.importService = Objects.requireNonNull(importService, "importService must not be null");
        this.priorPayrollService = Objects.requireNonNull(priorPayrollService, "priorPayrollService must not be null");
    }

    @GetMapping("/api/v1/payroll/prior-payroll/template")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<String> template() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=prior-payroll-template.csv")
                .body(importService.template());
    }

    @PostMapping("/api/v1/payroll/prior-payroll-imports")
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<PayRunApiResponse<PriorPayrollImportResponse>> importFile(
            @Valid @RequestBody PriorPayrollImportRequest request) {
        PriorPayrollImportResponse response =
                importService.importFile(request.documentId(), request.financialYear(), request.dryRun());
        return ResponseEntity.ok(PayRunApiResponse.ok("Prior payroll import processed", response));
    }

    @GetMapping("/api/v1/payroll/prior-payroll-imports/{id}")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<PriorPayrollImportResponse>> getImport(@PathVariable("id") UUID id) {
        PriorPayrollImportResponse response = importService.getImport(id);
        return ResponseEntity.ok(PayRunApiResponse.ok("Prior payroll import retrieved", response));
    }

    @GetMapping("/api/v1/payroll/prior-payroll-imports")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<Page<PriorPayrollImportResponse>>> listImports(Pageable pageable) {
        Page<PriorPayrollImportResponse> response = importService.listImports(pageable);
        return ResponseEntity.ok(PayRunApiResponse.ok("Prior payroll imports retrieved", response));
    }

    @GetMapping("/api/v1/payroll/prior-payroll")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<Page<PriorPayrollMonthResponse>>> months(
            @RequestParam("fy") String financialYear,
            @RequestParam(value = "employeeId", required = false) UUID employeeId,
            Pageable pageable) {
        Page<PriorPayrollMonthResponse> response = priorPayrollService.months(financialYear, employeeId, pageable);
        return ResponseEntity.ok(PayRunApiResponse.ok("Prior payroll months retrieved", response));
    }

    @DeleteMapping("/api/v1/payroll/prior-payroll/{id}")
    @RequiresAction("payroll.run.execute")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        priorPayrollService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/payroll/prior-payroll/status")
    @RequiresAction("payroll.run.read")
    public ResponseEntity<PayRunApiResponse<PriorPayrollStatusResponse>> status(
            @RequestParam("fy") String financialYear) {
        PriorPayrollStatusResponse response = priorPayrollService.status(financialYear);
        return ResponseEntity.ok(PayRunApiResponse.ok("Prior payroll status retrieved", response));
    }

    @ExceptionHandler(PriorPayrollNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PriorPayrollNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ApiError.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, ApiError.VALIDATION_FAILED, "Request body is unreadable or malformed");
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, ApiError code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, null));
    }
}
