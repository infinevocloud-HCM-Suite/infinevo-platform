package com.infinevo.payroll.taxcalc.recalc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxcalc.exception.RegimeNotAvailableException;
import com.infinevo.payroll.taxcalc.exception.TaxRulesMissingException;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for officer-triggered annual tax recalculation and history audit retrieval (W-33.3).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/employees/{employeeId}/tax-declaration/{fy}/tax")
public class TaxRecalculationController {

    private final TaxRecalculationService recalculationService;
    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;

    public TaxRecalculationController(
            TaxRecalculationService recalculationService, EmployeeService employeeService, ObjectMapper objectMapper) {
        this.recalculationService =
                Objects.requireNonNull(recalculationService, "recalculationService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @PostMapping("/recalculate")
    @RequiresAction("payroll.tax_declaration.manage")
    public ApiResponse<TaxComputationRecordResponse> recalculate(
            @PathVariable("employeeId") UUID employeeId, @PathVariable("fy") String financialYear) {
        UUID officerId =
                employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
        TaxComputationRecord record =
                recalculationService.recalculate(employeeId, financialYear, TaxTrigger.OFFICER, officerId);
        return ApiResponse.ok("Tax recalculated successfully", toResponse(record));
    }

    @GetMapping("/history")
    @RequiresAction("payroll.tax_declaration.read")
    public ApiResponse<List<TaxComputationRecordResponse>> history(
            @PathVariable("employeeId") UUID employeeId, @PathVariable("fy") String financialYear) {
        List<TaxComputationRecordResponse> records = recalculationService.history(employeeId, financialYear);
        return ApiResponse.ok("Tax calculation history retrieved successfully", records);
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

    private TaxComputationRecordResponse toResponse(TaxComputationRecord r) {
        Object workingObj;
        try {
            workingObj = objectMapper.readTree(r.getWorking());
        } catch (Exception e) {
            workingObj = r.getWorking();
        }

        return new TaxComputationRecordResponse(
                r.getId(),
                r.getEmployeeId(),
                r.getDeclarationId(),
                r.getFinancialYear(),
                r.getRegime(),
                r.getTrigger(),
                r.getGrossTotalIncome(),
                r.getHraExemption(),
                r.getStandardDeduction(),
                r.getProfessionalTax(),
                r.getHousePropertyIncome(),
                r.getOtherIncome(),
                r.getChapterVia(),
                r.getTaxableIncome(),
                r.getTaxBeforeRebate(),
                r.getRebate(),
                r.getSurcharge(),
                r.getCess(),
                r.getPrevEmployerTds(),
                r.getAnnualTax(),
                workingObj,
                r.getComputedAt(),
                r.getComputedBy());
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId != null ? traceId : "";
    }
}
