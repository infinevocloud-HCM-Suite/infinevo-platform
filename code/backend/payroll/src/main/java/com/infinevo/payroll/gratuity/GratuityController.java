package com.infinevo.payroll.gratuity;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.salary.SalaryNotFoundException;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.net.URI;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for gratuity estimation and settlement recording (W-91).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/gratuity")
public class GratuityController {

    private final GratuityService gratuityService;

    public GratuityController(GratuityService gratuityService) {
        this.gratuityService = Objects.requireNonNull(gratuityService, "gratuityService must not be null");
    }

    @PostMapping("/estimate")
    @RequiresAction("payroll.salary.manage")
    public GratuityEstimateResponse estimate(@RequestBody GratuityEstimateRequest request) {
        return gratuityService.estimateGratuity(request);
    }

    @PostMapping("/settlements")
    @RequiresAction("payroll.salary.manage")
    public ResponseEntity<GratuitySettlementResponse> createSettlement(
            @RequestBody GratuitySettlementCreateRequest request) {
        GratuitySettlementResponse created = gratuityService.createSettlement(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/gratuity/settlements/" + created.id()))
                .body(created);
    }

    @GetMapping("/settlements/{id}")
    @RequiresAction("payroll.salary.read")
    public GratuitySettlementResponse getSettlement(@PathVariable("id") UUID id) {
        return gratuityService.getSettlement(id);
    }

    @ExceptionHandler(GratuityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(GratuityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(EmployeeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEmployeeNotFound(EmployeeService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(SalaryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSalaryNotFound(SalaryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(GratuityConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(GratuityConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(GratuityValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(GratuityValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.getFieldErrors(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, "Malformed request body", traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}
