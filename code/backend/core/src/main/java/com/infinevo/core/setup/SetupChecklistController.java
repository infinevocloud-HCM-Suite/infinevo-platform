package com.infinevo.core.setup;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for tenant setup checklist (W-24.1).
 */
@RestController
@RequestMapping("/api/v1/setup-checklist")
public class SetupChecklistController {

    private final SetupChecklistService setupChecklistService;

    public SetupChecklistController(SetupChecklistService setupChecklistService) {
        this.setupChecklistService =
                Objects.requireNonNull(setupChecklistService, "setupChecklistService must not be null");
    }

    @GetMapping
    @RequiresAction("core.tenant.read")
    public ResponseEntity<SetupChecklistResponse> getChecklist() {
        UUID tenantId = TenantContext.require();
        return ResponseEntity.ok(setupChecklistService.getChecklist(tenantId));
    }

    @PostMapping("/{stepCode}/skip")
    @RequiresAction("core.tenant.manage")
    public ResponseEntity<SetupStepResponse> skipStep(
            @PathVariable("stepCode") String stepCode, @RequestBody(required = false) SkipStepRequest request) {
        UUID tenantId = TenantContext.require();
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("Skip reason must not be blank");
        }
        if (request.reason().trim().length() > 500) {
            throw new IllegalArgumentException("Skip reason must not exceed 500 characters");
        }
        return ResponseEntity.ok(setupChecklistService.skipStep(
                tenantId, stepCode, request.reason().trim()));
    }

    @ExceptionHandler(SetupChecklistService.SetupStepNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(SetupChecklistService.SetupStepNotFoundException e) {
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
