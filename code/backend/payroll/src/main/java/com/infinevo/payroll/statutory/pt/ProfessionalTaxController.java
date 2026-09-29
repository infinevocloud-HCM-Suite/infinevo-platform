package com.infinevo.payroll.statutory.pt;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.List;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for professional tax statutory settings and per-tenant overrides (W-31.2).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/settings/professional-tax")
public class ProfessionalTaxController {

    private final ProfessionalTaxService professionalTaxService;

    public ProfessionalTaxController(ProfessionalTaxService professionalTaxService) {
        this.professionalTaxService =
                Objects.requireNonNull(professionalTaxService, "professionalTaxService must not be null");
    }

    @GetMapping
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<PtApiResponse<List<PtStateResponse>>> getAllStates() {
        List<PtStateResponse> states = professionalTaxService.statesForTenant();
        return ResponseEntity.ok(PtApiResponse.ok("Professional tax settings fetched successfully", states));
    }

    @GetMapping("/{stateCode}")
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<PtApiResponse<PtStateResponse>> getState(@PathVariable String stateCode) {
        PtStateResponse response = professionalTaxService.getStateForTenant(stateCode);
        return ResponseEntity.ok(PtApiResponse.ok("Professional tax setting fetched successfully", response));
    }

    @PutMapping("/{stateCode}")
    @RequiresAction("payroll.settings.manage")
    public ResponseEntity<PtApiResponse<PtStateResponse>> putOverride(
            @PathVariable String stateCode, @RequestBody PtOverrideRequest request) {
        PtStateResponse response = professionalTaxService.setOverride(stateCode, request);
        return ResponseEntity.ok(PtApiResponse.ok("Professional tax override updated successfully", response));
    }

    @DeleteMapping("/{stateCode}/override")
    @RequiresAction("payroll.settings.manage")
    public ResponseEntity<Void> resetOverride(@PathVariable String stateCode) {
        professionalTaxService.resetOverride(stateCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{stateCode}/history")
    @RequiresAction("payroll.salary.read")
    public ResponseEntity<PtApiResponse<List<PtHistoryResponse>>> getHistory(@PathVariable String stateCode) {
        List<PtHistoryResponse> history = professionalTaxService.getHistory(stateCode);
        return ResponseEntity.ok(PtApiResponse.ok("Professional tax history fetched successfully", history));
    }

    @ExceptionHandler(PtNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PtNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler({PtValidationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleValidation(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    private String traceId() {
        String id = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return id != null ? id : "";
    }
}
