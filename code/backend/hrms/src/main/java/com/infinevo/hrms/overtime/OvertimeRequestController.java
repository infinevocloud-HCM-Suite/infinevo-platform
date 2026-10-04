package com.infinevo.hrms.overtime;

import com.infinevo.core.overtime.OvertimeResponse;
import com.infinevo.core.overtime.OvertimeService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.entitlement.RequiresModule;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Overtime requests (W-40.6 §4). Approving and rejecting go through {@code core}'s
 * {@code POST /api/v1/approvals/steps/{stepId}/decide}; nothing here decides. HR's list of every request is
 * {@code core}'s {@code GET /api/v1/overtime}.
 */
@RestController
@RequestMapping("/api/v1/hrms/overtime-requests")
@RequiresModule(PlatformModule.HRMS)
public class OvertimeRequestController {

    private final OvertimeRequestWorkflowService workflowService;

    public OvertimeRequestController(OvertimeRequestWorkflowService workflowService) {
        this.workflowService = Objects.requireNonNull(workflowService, "workflowService must not be null");
    }

    @PostMapping
    @RequiresAction("hrms.overtime.request")
    public ResponseEntity<OvertimeResponse> submit(@RequestBody OvertimeRequestSubmission submission) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.submit(submission));
    }

    @GetMapping("/mine")
    @RequiresAction("hrms.overtime.request")
    public ResponseEntity<List<OvertimeResponse>> mine(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(workflowService.mine(from, to));
    }

    @GetMapping("/{id}")
    @RequiresAction(
            value = "hrms.overtime.request",
            anyOf = {"core.overtime.read", "core.approval.decide"})
    public ResponseEntity<OvertimeResponse> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(workflowService.get(id));
    }

    @ExceptionHandler(OvertimeService.NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(OvertimeService.NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(OvertimeService.ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(OvertimeService.ValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.validation(e.fieldErrors(), traceId()));
    }

    /**
     * The approval engine refuses to start when the tenant has no active {@code OVERTIME} definition
     * ({@code ApprovalService.start}); the submit has rolled back by then. Not the caller's input, so {@code 409}.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ApiError.VALIDATION_FAILED, "Request body is not readable", traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
