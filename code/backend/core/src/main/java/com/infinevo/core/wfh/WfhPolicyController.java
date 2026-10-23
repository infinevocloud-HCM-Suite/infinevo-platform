package com.infinevo.core.wfh;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wfh-policies")
public class WfhPolicyController {

    private final WfhPolicyService policyService;

    public WfhPolicyController(WfhPolicyService policyService) {
        this.policyService = Objects.requireNonNull(policyService, "policyService must not be null");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.wfh.manage")
    public WfhPolicyResponse createPolicy(@RequestBody CreateWfhPolicyRequest request) {
        UUID tenantId = TenantContext.require();
        return policyService.createPolicy(tenantId, request);
    }

    @GetMapping
    @RequiresAction(value = "core.wfh.manage", anyOf = "core.wfh.read")
    public List<WfhPolicyResponse> listPolicies() {
        UUID tenantId = TenantContext.require();
        return policyService.listPolicies(tenantId);
    }

    @GetMapping("/default")
    @RequiresAction(value = "core.wfh.manage", anyOf = "core.wfh.read")
    public WfhPolicyResponse getDefaultPolicy() {
        UUID tenantId = TenantContext.require();
        WfhPolicy policy = policyService.getOrCreateDefaultPolicy(tenantId);
        return WfhPolicyResponse.from(policy);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
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
