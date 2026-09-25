package com.infinevo.core.subscription;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for tenant subscription endpoints (W-12.1).
 */
@RestController
@RequestMapping("/api/v1/tenants/{id}/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = Objects.requireNonNull(subscriptionService, "subscriptionService must not be null");
    }

    @GetMapping
    @RequiresAction("core.tenant.read")
    public ResponseEntity<SubscriptionResponse> getSubscription(@PathVariable("id") UUID id) {
        UUID boundTenant = TenantContext.require();
        if (!boundTenant.equals(id)) {
            throw new SubscriptionNotFoundException(id);
        }
        return ResponseEntity.ok(subscriptionService.getSubscription(id));
    }

    @PutMapping("/modules")
    @RequiresAction("core.tenant.provision")
    public ResponseEntity<SubscriptionResponse> updateModules(
            @PathVariable("id") UUID id, @RequestBody ModulesUpdateRequest request) {
        if (request == null || request.modules() == null) {
            throw new IllegalArgumentException("modules must not be null");
        }
        return ResponseEntity.ok(subscriptionService.updateModules(id, request.modules()));
    }

    @PutMapping("/status")
    @RequiresAction("core.tenant.provision")
    public ResponseEntity<SubscriptionResponse> updateStatus(
            @PathVariable("id") UUID id, @RequestBody StatusUpdateRequest request) {
        if (request == null || request.status() == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return ResponseEntity.ok(subscriptionService.updateStatus(id, request.status()));
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(SubscriptionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED,
                        "The request body could not be read. Check the JSON is well formed.",
                        traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
