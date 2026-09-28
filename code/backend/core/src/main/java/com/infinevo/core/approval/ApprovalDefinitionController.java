package com.infinevo.core.approval;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for managing tenant approval definitions (W-15.1, spec section 4).
 */
@RestController
public class ApprovalDefinitionController {

    private final ApprovalDefinitionService service;

    public ApprovalDefinitionController(ApprovalDefinitionService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @GetMapping("/api/v1/approval-definitions")
    @RequiresAction("core.approval_definition.manage")
    public List<ApprovalDefinitionResponse> getApprovalDefinitions(
            @RequestParam(name = "flowType", required = false) ApprovalFlowType flowType,
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        if (flowType != null && asOf != null) {
            return service.getEffectiveDefinition(tenantId, flowType, asOf)
                    .map(List::of)
                    .orElseGet(List::of);
        } else if (flowType != null) {
            return service.getDefinitionsByFlowType(tenantId, flowType);
        } else {
            return service.getAllDefinitions(tenantId);
        }
    }

    @PutMapping("/api/v1/approval-definitions/{flowType}")
    @RequiresAction("core.approval_definition.manage")
    public ApprovalDefinitionResponse putApprovalDefinition(
            @PathVariable("flowType") ApprovalFlowType flowType, @RequestBody ApprovalDefinitionRequest request) {
        UUID tenantId = TenantContext.require();
        return service.saveDefinition(tenantId, flowType, request);
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
