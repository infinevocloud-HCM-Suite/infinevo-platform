package com.infinevo.payroll.component;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 * REST controller for deduction salary components (W-26.1).
 */
@RestController
@RequiresModule(PlatformModule.PAYROLL)
@RequestMapping("/api/v1/payroll/components/deductions")
public class DeductionController {

    private final DeductionService deductionService;

    public DeductionController(DeductionService deductionService) {
        this.deductionService = Objects.requireNonNull(deductionService, "deductionService must not be null");
    }

    @PostMapping
    @RequiresAction("payroll.structure.manage")
    public ResponseEntity<DeductionResponse> create(@RequestBody DeductionRequest request) {
        DeductionResponse created = deductionService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/components/deductions/" + created.id()))
                .body(created);
    }

    @GetMapping
    @RequiresAction("payroll.structure.read")
    public List<DeductionResponse> list(@RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return deductionService.list(activeOnly);
    }

    @GetMapping("/{id}")
    @RequiresAction("payroll.structure.read")
    public DeductionResponse get(@PathVariable("id") UUID id) {
        return deductionService.get(id);
    }

    @PutMapping("/{id}")
    @RequiresAction("payroll.structure.manage")
    public DeductionResponse update(@PathVariable("id") UUID id, @RequestBody DeductionRequest request) {
        return deductionService.update(id, request);
    }

    @PutMapping("/{id}/active")
    @RequiresAction("payroll.structure.manage")
    public DeductionResponse updateActive(@PathVariable("id") UUID id, @RequestBody ActiveUpdateRequest request) {
        if (request == null || request.active() == null) {
            throw new ComponentValidationException("active", "Active flag must not be null");
        }
        return deductionService.updateActive(id, request.active());
    }

    @DeleteMapping("/{id}")
    @RequiresAction("payroll.structure.manage")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        deductionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ComponentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ComponentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(ComponentValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ComponentValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.getFieldErrors(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}
