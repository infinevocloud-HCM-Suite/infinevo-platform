package com.infinevo.payroll.challan;

import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for ITNS 281 TDS Challan recording and allocation (W-89.1).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/compliance/challans")
public class TdsChallanController {

    private final TdsChallanService challanService;

    public TdsChallanController(TdsChallanService challanService) {
        this.challanService = Objects.requireNonNull(challanService, "challanService must not be null");
    }

    @PostMapping
    @RequiresAction(
            value = "payroll.tax.manage",
            anyOf = {"payroll.settings.manage"})
    public ResponseEntity<ApiResponse<TdsChallanResponse>> create(@RequestBody TdsChallanRequest request) {
        TdsChallanResponse response = challanService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("TDS Challan created successfully", response));
    }

    @GetMapping
    @RequiresAction(
            value = "payroll.tax.manage",
            anyOf = {"payroll.tax.view", "payroll.settings.manage", "payroll.statutory_report.read"})
    public ResponseEntity<ApiResponse<List<TdsChallanResponse>>> list(
            @RequestParam("financialYear") String financialYear,
            @RequestParam(value = "quarter", required = false) String quarter) {
        List<TdsChallanResponse> list = challanService.list(financialYear, quarter);
        return ResponseEntity.ok(ApiResponse.ok("TDS Challans retrieved successfully", list));
    }

    @GetMapping("/{id}")
    @RequiresAction(
            value = "payroll.tax.manage",
            anyOf = {"payroll.tax.view", "payroll.settings.manage", "payroll.statutory_report.read"})
    public ResponseEntity<ApiResponse<TdsChallanResponse>> get(@PathVariable("id") UUID id) {
        TdsChallanResponse response = challanService
                .findById(id)
                .orElseThrow(() -> new TdsChallanNotFoundException("TDS Challan not found: " + id));
        return ResponseEntity.ok(ApiResponse.ok("TDS Challan retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @RequiresAction(
            value = "payroll.tax.manage",
            anyOf = {"payroll.settings.manage"})
    public ResponseEntity<ApiResponse<TdsChallanResponse>> update(
            @PathVariable("id") UUID id, @RequestBody TdsChallanRequest request) {
        TdsChallanResponse response = challanService.update(id, request);
        return ResponseEntity.ok(ApiResponse.ok("TDS Challan updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @RequiresAction(
            value = "payroll.tax.manage",
            anyOf = {"payroll.settings.manage"})
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        challanService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/allocate")
    @RequiresAction(
            value = "payroll.tax.manage",
            anyOf = {"payroll.settings.manage"})
    public ResponseEntity<ApiResponse<TdsChallanResponse>> allocate(
            @PathVariable("id") UUID id, @RequestBody ChallanAllocationRequest request) {
        TdsChallanResponse response = challanService.allocate(id, request);
        return ResponseEntity.ok(ApiResponse.ok("TDS Challan allocated successfully", response));
    }

    @GetMapping("/{id}/allocations")
    @RequiresAction(
            value = "payroll.tax.manage",
            anyOf = {"payroll.tax.view", "payroll.settings.manage", "payroll.statutory_report.read"})
    public ResponseEntity<ApiResponse<List<TdsChallanAllocation>>> listAllocations(@PathVariable("id") UUID id) {
        List<TdsChallanAllocation> list = challanService.listAllocations(id);
        return ResponseEntity.ok(ApiResponse.ok("TDS Challan allocations retrieved successfully", list));
    }

    @ExceptionHandler(TdsChallanValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(TdsChallanValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, ex.getMessage(), getTraceId()));
    }

    @ExceptionHandler(TdsChallanNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(TdsChallanNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, ex.getMessage(), getTraceId()));
    }

    private String getTraceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
