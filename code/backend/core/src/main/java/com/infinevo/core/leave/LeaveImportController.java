package com.infinevo.core.leave;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.tenant.TenantContext;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for bulk leave allocation imports (W-16.4b, spec section 4).
 */
@RestController
public class LeaveImportController {

    private final LeaveImportService leaveImportService;

    public LeaveImportController(LeaveImportService leaveImportService) {
        this.leaveImportService = Objects.requireNonNull(leaveImportService, "leaveImportService must not be null");
    }

    @PostMapping("/api/v1/leave-imports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequiresAction("core.leave_balance.manage")
    public LeaveImportResultResponse importLeaves(@RequestBody LeaveImportRequest request) {
        UUID tenantId = TenantContext.require();
        if (request == null || request.documentId() == null || request.leaveYear() == null) {
            throw new IllegalArgumentException("documentId and leaveYear must be provided");
        }
        return leaveImportService.importLeaves(tenantId, request.documentId(), request.leaveYear(), request.dryRun());
    }

    @GetMapping("/api/v1/leave-imports/{id}")
    @RequiresAction("core.leave_balance.manage")
    public LeaveImportResultResponse getImport(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        return leaveImportService.getImport(tenantId, id);
    }

    @GetMapping("/api/v1/leave-imports")
    @RequiresAction("core.leave_balance.manage")
    public Page<LeaveImportResultResponse> listImports(Pageable pageable) {
        UUID tenantId = TenantContext.require();
        return leaveImportService.listImports(tenantId, pageable);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException e) {
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("not found")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        ApiError.VALIDATION_FAILED, "Request body is unreadable or malformed", traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, msg, traceId()));
    }

    private String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
