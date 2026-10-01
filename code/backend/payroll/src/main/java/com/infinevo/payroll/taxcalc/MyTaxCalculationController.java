package com.infinevo.payroll.taxcalc;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxcalc.dto.TaxComputationResponse;
import com.infinevo.payroll.taxcalc.dto.TaxComputeResponse;
import com.infinevo.payroll.taxcalc.exception.RegimeNotAvailableException;
import com.infinevo.payroll.taxcalc.exception.TaxRulesMissingException;
import com.infinevo.payroll.taxcalc.model.TaxComputation;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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
 * Self-service controller for employee tax calculations (W-33.1).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/me/tax-declaration/{fy}/tax")
public class MyTaxCalculationController {

    private final TaxCalculationService taxCalculationService;
    private final EmployeeService employeeService;

    public MyTaxCalculationController(TaxCalculationService taxCalculationService, EmployeeService employeeService) {
        this.taxCalculationService =
                Objects.requireNonNull(taxCalculationService, "taxCalculationService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @GetMapping
    @RequiresAction("payroll.tax_declaration.read_own")
    public ApiResponse<TaxComputationResponse> getOwn(
            @PathVariable("fy") String financialYear, @RequestParam(name = "regime", required = false) String regime) {
        EmployeeResponse employee = currentEmployeeOrDeny("payroll.tax_declaration.read_own");
        FinancialYear fy = FinancialYear.parse(financialYear);
        TaxRegime targetRegime = (regime != null && !regime.isBlank()) ? TaxRegime.from(regime) : null;
        TaxComputation computation = taxCalculationService.compute(employee.id(), fy, targetRegime);
        return ApiResponse.ok(
                "Tax computation preview retrieved successfully", TaxComputationResponse.from(computation));
    }

    @PostMapping("/compute")
    @RequiresAction("payroll.tax_declaration.declare_own")
    public ApiResponse<TaxComputeResponse> computeOwn(@PathVariable("fy") String financialYear) {
        EmployeeResponse employee = currentEmployeeOrDeny("payroll.tax_declaration.declare_own");
        FinancialYear fy = FinancialYear.parse(financialYear);
        Map<TaxRegime, TaxComputation> computations = taxCalculationService.computeAndRecord(employee.id(), fy);
        return ApiResponse.ok("Tax computed and recorded successfully", TaxComputeResponse.of(computations));
    }

    private EmployeeResponse currentEmployeeOrDeny(String actionCode) {
        return employeeService.currentEmployee().orElseThrow(() -> new PermissionDeniedException(actionCode));
    }

    @ExceptionHandler(DeclarationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(DeclarationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(RegimeNotAvailableException.class)
    public ResponseEntity<ApiErrorResponse> handleRegimeNotAvailable(RegimeNotAvailableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(
                        "REGIME_NOT_AVAILABLE",
                        e.getMessage(),
                        Map.of("regime", e.getRegime()),
                        traceId(),
                        Instant.now()));
    }

    @ExceptionHandler(TaxRulesMissingException.class)
    public ResponseEntity<ApiErrorResponse> handleTaxRulesMissing(TaxRulesMissingException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponse(
                        "TAX_RULES_MISSING",
                        e.getMessage(),
                        Map.of("rule_table", e.getRuleTable(), "financial_year", e.getFinancialYear()),
                        traceId(),
                        Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId != null ? traceId : "";
    }
}
