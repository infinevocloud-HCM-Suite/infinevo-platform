package com.infinevo.hrms.exit;

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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for employee resignation self-service and HR exit clearance management (W-103).
 */
@RestController
@RequiresModule(PlatformModule.HRMS)
public class ExitController {

    private final ExitService exitService;

    public ExitController(ExitService exitService) {
        this.exitService = Objects.requireNonNull(exitService, "exitService must not be null");
    }

    @PostMapping("/api/v1/me/exit")
    @RequiresAction("hrms.leave.apply")
    public ResponseEntity<ExitRequestResponse> submitResignation(@RequestBody SubmitResignationRequest request) {
        ExitRequestResponse created = exitService.submitResignation(request);
        return ResponseEntity.created(URI.create("/api/v1/hrms/exits/" + created.id()))
                .body(created);
    }

    @GetMapping("/api/v1/me/exit")
    @RequiresAction("hrms.leave.read_own")
    public List<ExitRequestResponse> myExits() {
        return exitService.getMyExits();
    }

    @PostMapping("/api/v1/me/exit/{id}/withdraw")
    @RequiresAction("hrms.leave.apply")
    public ExitRequestResponse withdraw(@PathVariable("id") UUID id) {
        return exitService.withdrawMyExit(id);
    }

    @GetMapping("/api/v1/hrms/exits")
    @RequiresAction("core.employee.read")
    public List<ExitRequestResponse> listExits(@RequestParam(name = "status", required = false) ExitStatus status) {
        return exitService.listAllExits(status);
    }

    @GetMapping("/api/v1/hrms/exits/{id}")
    @RequiresAction("core.employee.read")
    public ExitRequestResponse getExit(@PathVariable("id") UUID id) {
        return exitService.getExit(id);
    }

    @PostMapping("/api/v1/hrms/exits/{id}/approve")
    @RequiresAction(
            value = "core.approval.decide",
            anyOf = {"core.employee.manage"})
    public ExitRequestResponse approveExit(
            @PathVariable("id") UUID id, @RequestBody(required = false) ApproveExitRequest request) {
        return exitService.approveExit(id, request);
    }

    @PostMapping("/api/v1/hrms/exits/{id}/reject")
    @RequiresAction(
            value = "core.approval.decide",
            anyOf = {"core.employee.manage"})
    public ExitRequestResponse rejectExit(
            @PathVariable("id") UUID id, @RequestBody(required = false) RejectExitRequest request) {
        return exitService.rejectExit(id, request);
    }

    @PutMapping("/api/v1/hrms/exits/{id}/clearance/{dept}")
    @RequiresAction("core.employee.manage")
    public ExitRequestResponse signOffDepartment(
            @PathVariable("id") UUID id,
            @PathVariable("dept") ClearanceDepartment dept,
            @RequestBody(required = false) UpdateClearanceRequest request) {
        return exitService.signOffDepartmentClearance(id, dept, request);
    }

    @PutMapping("/api/v1/hrms/exits/{id}/clearance/items/{itemId}")
    @RequiresAction("core.employee.manage")
    public ExitRequestResponse updateClearanceItem(
            @PathVariable("id") UUID id,
            @PathVariable("itemId") UUID itemId,
            @RequestBody(required = false) UpdateClearanceRequest request) {
        return exitService.updateClearanceItem(id, itemId, request);
    }

    @PostMapping("/api/v1/hrms/exits/{id}/hand-off-to-payroll")
    @RequiresAction("core.employee.manage")
    public PayrollHandOffPayload handOffToPayroll(@PathVariable("id") UUID id) {
        return exitService.handOffToPayroll(id);
    }

    @ExceptionHandler(ExitNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ExitNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(ApiError.NOT_FOUND, e.getMessage(), traceId()));
    }

    @ExceptionHandler(ExitConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ExitConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(ApiError.CONFLICT, e.getMessage(), traceId()));
    }

    @ExceptionHandler(ExitValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ExitValidationException e) {
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
