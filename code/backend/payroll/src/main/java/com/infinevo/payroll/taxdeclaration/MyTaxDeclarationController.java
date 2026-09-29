package com.infinevo.payroll.taxdeclaration;

import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for self-service employee tax declarations (W-32.1).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/me/tax-declaration")
public class MyTaxDeclarationController {

    private final TaxDeclarationService taxDeclarationService;

    public MyTaxDeclarationController(TaxDeclarationService taxDeclarationService) {
        this.taxDeclarationService =
                Objects.requireNonNull(taxDeclarationService, "taxDeclarationService must not be null");
    }

    @GetMapping("/{fy}")
    @RequiresAction("payroll.tax_declaration.read_own")
    public ApiResponse<TaxDeclarationResponse> getOwn(@PathVariable("fy") String financialYear) {
        TaxDeclarationResponse response = taxDeclarationService.readOwn(financialYear);
        return ApiResponse.ok("Tax declaration retrieved successfully", response);
    }

    @PutMapping("/{fy}")
    @RequiresAction("payroll.tax_declaration.declare_own")
    public ApiResponse<TaxDeclarationResponse> saveOwn(
            @PathVariable("fy") String financialYear, @RequestBody TaxDeclarationRequest request) {
        TaxDeclarationResponse response = taxDeclarationService.saveOwn(financialYear, request);
        return ApiResponse.ok("Tax declaration updated successfully", response);
    }

    @PostMapping("/{fy}/submit")
    @RequiresAction("payroll.tax_declaration.declare_own")
    public ApiResponse<TaxDeclarationResponse> submitOwn(@PathVariable("fy") String financialYear) {
        TaxDeclarationResponse response = taxDeclarationService.submitOwn(financialYear);
        return ApiResponse.ok("Tax declaration submitted successfully", response);
    }

    @PostMapping("/{fy}/reopen")
    @RequiresAction("payroll.tax_declaration.declare_own")
    public ApiResponse<TaxDeclarationResponse> reopenOwn(@PathVariable("fy") String financialYear) {
        TaxDeclarationResponse response = taxDeclarationService.reopenOwn(financialYear);
        return ApiResponse.ok("Tax declaration reopened successfully", response);
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
