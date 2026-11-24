package com.infinevo.payroll.taxcalc.recalc;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Open tax recalculation failures and their retry, for the payroll officer (D-147).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/tax-recalc-failures")
public class TaxRecalcFailureController {

    private final TaxRecalcFailureService failureService;
    private final EmployeeService employeeService;

    public TaxRecalcFailureController(TaxRecalcFailureService failureService, EmployeeService employeeService) {
        this.failureService = Objects.requireNonNull(failureService, "failureService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @GetMapping
    @RequiresAction("payroll.tax_declaration.read")
    public ApiResponse<List<TaxRecalcFailureResponse>> open(
            @RequestParam(name = "fy", required = false) String financialYear) {
        return ApiResponse.ok("Open tax recalculation failures retrieved", failureService.open(financialYear));
    }

    @PostMapping("/{id}/retry")
    @RequiresAction("payroll.tax_declaration.verify")
    public ApiResponse<Map<String, Object>> retry(@PathVariable("id") UUID id) {
        UUID officerId =
                employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
        TaxComputationRecord record = failureService.retry(id, officerId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tax_computation_id", record.getId());
        body.put("employee_id", record.getEmployeeId());
        body.put("financial_year", record.getFinancialYear());
        body.put("trigger", record.getTrigger());
        body.put("annual_tax", record.getAnnualTax());
        return ApiResponse.ok("Tax recalculated successfully", body);
    }

    @ExceptionHandler(TaxRecalcFailureNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(TaxRecalcFailureNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(TaxRecalcRetryFailedException.class)
    public ResponseEntity<ApiErrorResponse> handleRetryFailed(TaxRecalcRetryFailedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponse("TAX_RECALC_FAILED", e.getMessage(), Map.of(), traceId(), Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
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
