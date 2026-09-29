package com.infinevo.payroll.taxdeclaration;

import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowResponse;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
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
 * Controller for tenant-wide tax declaration window settings (W-32.1).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/tax-declaration/settings")
public class TaxDeclarationSettingsController {

    private final TaxDeclarationWindowService windowService;

    public TaxDeclarationSettingsController(TaxDeclarationWindowService windowService) {
        this.windowService = Objects.requireNonNull(windowService, "windowService must not be null");
    }

    @GetMapping("/{fy}")
    @RequiresAction("payroll.settings.manage")
    public ApiResponse<TaxDeclarationWindowResponse> get(@PathVariable("fy") String financialYear) {
        TaxDeclarationWindowResponse response = windowService.get(financialYear);
        return ApiResponse.ok("Window settings retrieved successfully", response);
    }

    @PutMapping("/{fy}")
    @RequiresAction("payroll.settings.manage")
    public ApiResponse<TaxDeclarationWindowResponse> upsert(
            @PathVariable("fy") String financialYear, @RequestBody TaxDeclarationWindowRequest request) {
        TaxDeclarationWindowResponse response = windowService.upsert(financialYear, request);
        return ApiResponse.ok("Window settings updated successfully", response);
    }

    @ExceptionHandler(WindowValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleWindowValidation(WindowValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
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
