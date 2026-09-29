package com.infinevo.payroll.taxdeclaration.deductions;

import com.infinevo.payroll.taxdeclaration.deductions.dto.DeductionDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PreTaxDeductionRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PrevEmploymentRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6AItemResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6ALineRequest;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotFoundException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for self-service employee Section 6A, pre-tax deductions, and previous employment (W-32.3).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/me/tax-declaration/{fy}")
public class MyDeductionDeclarationController {

    private final DeductionDeclarationService deductionService;

    public MyDeductionDeclarationController(DeductionDeclarationService deductionService) {
        this.deductionService = Objects.requireNonNull(deductionService, "deductionService must not be null");
    }

    @GetMapping("/section6a-items")
    @RequiresAction("payroll.tax_declaration.read_own")
    public ApiResponse<List<Section6AItemResponse>> getSection6AItems(@PathVariable("fy") String financialYear) {
        List<Section6AItemResponse> response = deductionService.getSection6AItemsOwn(financialYear);
        return ApiResponse.ok("Section 6A catalogue items retrieved successfully", response);
    }

    @GetMapping("/deductions")
    @RequiresAction("payroll.tax_declaration.read_own")
    public ApiResponse<DeductionDeclarationResponse> getOwnDeductions(@PathVariable("fy") String financialYear) {
        DeductionDeclarationResponse response = deductionService.readOwn(financialYear);
        return ApiResponse.ok("Deduction declaration retrieved successfully", response);
    }

    @PutMapping("/section6a")
    @RequiresAction("payroll.tax_declaration.declare_own")
    public ApiResponse<DeductionDeclarationResponse> replaceSection6A(
            @PathVariable("fy") String financialYear, @RequestBody List<Section6ALineRequest> requests) {
        DeductionDeclarationResponse response = deductionService.replaceSection6AOwn(financialYear, requests);
        return ApiResponse.ok("Section 6A declaration updated successfully", response);
    }

    @PutMapping("/pre-tax-deductions")
    @RequiresAction("payroll.tax_declaration.declare_own")
    public ApiResponse<DeductionDeclarationResponse> replacePreTaxDeductions(
            @PathVariable("fy") String financialYear, @RequestBody List<PreTaxDeductionRequest> requests) {
        DeductionDeclarationResponse response = deductionService.replacePreTaxDeductionsOwn(financialYear, requests);
        return ApiResponse.ok("Pre-tax deductions updated successfully", response);
    }

    @PutMapping("/previous-employment")
    @RequiresAction("payroll.tax_declaration.declare_own")
    public ApiResponse<DeductionDeclarationResponse> replacePrevEmployment(
            @PathVariable("fy") String financialYear, @RequestBody List<PrevEmploymentRequest> requests) {
        DeductionDeclarationResponse response = deductionService.replacePrevEmploymentOwn(financialYear, requests);
        return ApiResponse.ok("Previous employment declaration updated successfully", response);
    }

    @ExceptionHandler(DeclarationNotEditableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotEditable(DeclarationNotEditableException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(e.reasonCode(), e.getMessage(), Map.of(), traceId(), Instant.now()));
    }

    @ExceptionHandler(WindowValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(WindowValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(DeclarationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(DeclarationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
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
