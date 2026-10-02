package com.infinevo.payroll.tds;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxcalc.TaxRegime;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.payroll.tds.dto.EmployeeTdsResponse;
import com.infinevo.payroll.tds.dto.RecordTdsRequest;
import com.infinevo.payroll.tds.exception.EmployeeTdsConflictException;
import com.infinevo.payroll.tds.exception.EmployeeTdsNotFoundException;
import com.infinevo.payroll.tds.exception.EmployeeTdsValidationException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for employee TDS annual records and pay run tax lines (W-36.1 §4).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
public class EmployeeTdsController {

    private final EmployeeTdsService tdsService;
    private final EmployeeService employeeService;

    public EmployeeTdsController(EmployeeTdsService tdsService, EmployeeService employeeService) {
        this.tdsService = Objects.requireNonNull(tdsService, "tdsService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @PutMapping("/api/v1/payroll/employees/{employeeId}/tds/{fy}")
    @RequiresAction("payroll.tax_declaration.verify")
    public ApiResponse<EmployeeTdsResponse> put(
            @PathVariable("employeeId") UUID employeeId,
            @PathVariable("fy") String financialYear,
            @RequestBody RecordTdsRequest request) {
        if (request == null) {
            throw new EmployeeTdsValidationException("Request body must not be null");
        }
        TaxRegime regime;
        try {
            regime = TaxRegime.from(request.regime());
        } catch (IllegalArgumentException e) {
            throw new EmployeeTdsValidationException(
                    "Invalid tax regime: " + request.regime() + " (expected OLD or NEW)");
        }

        if (request.annualGross() == null || request.annualTaxableIncome() == null || request.annualTax() == null) {
            throw new EmployeeTdsValidationException("annual_gross, annual_taxable_income and annual_tax are required");
        }

        TdsFigures figures = new TdsFigures(
                regime,
                request.annualGross(),
                request.annualTaxableIncome(),
                request.annualTax(),
                request.effectiveFromPeriod(),
                null,
                request.note(),
                TdsSource.OFFICER);

        EmployeeTds saved = tdsService.record(employeeId, financialYear, figures);
        BigDecimal ytd = tdsService.yearToDate(employeeId, financialYear);
        BigDecimal remaining = calculateRemaining(saved.getAnnualTax(), ytd);

        return ApiResponse.ok("TDS record saved successfully", EmployeeTdsResponse.from(saved, ytd, remaining));
    }

    @GetMapping("/api/v1/payroll/employees/{employeeId}/tds/{fy}")
    @RequiresAction("payroll.tax_declaration.read")
    public ApiResponse<EmployeeTdsResponse> get(
            @PathVariable("employeeId") UUID employeeId, @PathVariable("fy") String financialYear) {
        employeeService.get(employeeId);
        EmployeeTds active = tdsService
                .active(employeeId, financialYear)
                .orElseThrow(() -> new EmployeeTdsNotFoundException(employeeId, financialYear));
        BigDecimal ytd = tdsService.yearToDate(employeeId, financialYear);
        BigDecimal remaining = calculateRemaining(active.getAnnualTax(), ytd);

        return ApiResponse.ok("TDS record retrieved successfully", EmployeeTdsResponse.from(active, ytd, remaining));
    }

    @GetMapping("/api/v1/payroll/employees/{employeeId}/tds/{fy}/history")
    @RequiresAction("payroll.tax_declaration.read")
    public ApiResponse<List<EmployeeTdsResponse>> history(
            @PathVariable("employeeId") UUID employeeId, @PathVariable("fy") String financialYear) {
        List<EmployeeTds> history = tdsService.history(employeeId, financialYear);
        BigDecimal ytd = tdsService.yearToDate(employeeId, financialYear);
        List<EmployeeTdsResponse> responses = history.stream()
                .map(record -> EmployeeTdsResponse.from(record, ytd, calculateRemaining(record.getAnnualTax(), ytd)))
                .toList();

        return ApiResponse.ok("TDS record history retrieved successfully", responses);
    }

    @GetMapping("/api/v1/me/tds/{fy}")
    @RequiresAction("payroll.tax_declaration.read_own")
    public ApiResponse<EmployeeTdsResponse> getOwn(@PathVariable("fy") String financialYear) {
        EmployeeResponse current = employeeService
                .currentEmployee()
                .orElseThrow(
                        () -> new EmployeeTdsNotFoundException("No employee profile found for authenticated user"));
        EmployeeTds active = tdsService
                .active(current.id(), financialYear)
                .orElseThrow(() -> new EmployeeTdsNotFoundException(current.id(), financialYear));
        BigDecimal ytd = tdsService.yearToDate(current.id(), financialYear);
        BigDecimal remaining = calculateRemaining(active.getAnnualTax(), ytd);

        return ApiResponse.ok("TDS record retrieved successfully", EmployeeTdsResponse.from(active, ytd, remaining));
    }

    private static BigDecimal calculateRemaining(BigDecimal annualTax, BigDecimal ytd) {
        BigDecimal remaining = annualTax.subtract(ytd);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO.setScale(4) : remaining;
    }

    @ExceptionHandler(EmployeeTdsNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EmployeeTdsNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(EmployeeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEmployeeNotFound(EmployeeService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(EmployeeTdsValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(EmployeeTdsValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(EmployeeTdsConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(EmployeeTdsConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    private static String traceId() {
        return UUID.randomUUID().toString();
    }
}
