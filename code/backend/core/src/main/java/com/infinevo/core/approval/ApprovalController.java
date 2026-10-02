package com.infinevo.core.approval;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for approval instances and decision execution (W-15.2, spec section 4).
 */
@RestController
public class ApprovalController {

    private final ApprovalService approvalService;
    private final EscalationService escalationService;

    @org.springframework.beans.factory.annotation.Autowired
    public ApprovalController(
            ApprovalService approvalService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    EscalationService escalationService) {
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.escalationService = escalationService;
    }

    @GetMapping("/api/v1/approvals/pending")
    @RequiresAction("core.approval.decide")
    public Page<ApprovalStepResponse> getPendingApprovals(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return approvalService.getPendingSteps(PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/api/v1/approvals/{instanceId}")
    @RequiresAction("core.approval.decide")
    public ApprovalInstanceDetailResponse getApprovalInstance(@PathVariable("instanceId") UUID instanceId) {
        return approvalService.getInstance(instanceId);
    }

    @GetMapping("/api/v1/approvals/{instanceId}/history")
    @RequiresAction(
            value = "core.approval.read",
            anyOf = {"core.leave.read_own", "core.leave.read", "core.approval.decide"})
    public ApprovalHistoryResponse getApprovalHistory(@PathVariable("instanceId") UUID instanceId) {
        return approvalService.getHistory(instanceId);
    }

    @PostMapping("/api/v1/approvals/{instanceId}/reassign")
    @RequiresAction("core.approval.manage")
    public ResponseEntity<ApprovalStepResponse> reassignStep(
            @PathVariable("instanceId") UUID instanceId, @RequestBody ApprovalReassignRequest request) {
        UUID tenantId = com.infinevo.shared.tenant.TenantContext.require();
        if (escalationService == null) {
            throw new IllegalStateException("Escalation service is not available");
        }
        ApprovalStepResponse response = escalationService.reassign(tenantId, instanceId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/approvals/steps/{stepId}/decide")
    @RequiresAction("core.approval.decide")
    public ResponseEntity<Void> decideStep(
            @PathVariable("stepId") UUID stepId, @RequestBody ApprovalDecideRequest request) {
        approvalService.decide(stepId, request);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
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
