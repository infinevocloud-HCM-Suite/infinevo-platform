package com.infinevo.core.leave;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for leave types, policy configurations, and eligibility queries (W-16.1, spec section 4).
 */
@RestController
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;
    private final LeaveEligibilityService leaveEligibilityService;

    public LeaveTypeController(LeaveTypeService leaveTypeService, LeaveEligibilityService leaveEligibilityService) {
        this.leaveTypeService = Objects.requireNonNull(leaveTypeService, "leaveTypeService must not be null");
        this.leaveEligibilityService =
                Objects.requireNonNull(leaveEligibilityService, "leaveEligibilityService must not be null");
    }

    @PostMapping("/api/v1/leave-types")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.leave_type.manage")
    public LeaveTypeResponse createLeaveType(@RequestBody LeaveTypeRequest request) {
        UUID tenantId = TenantContext.require();
        return leaveTypeService.createLeaveType(tenantId, request);
    }

    @GetMapping("/api/v1/leave-types")
    @RequiresAction(
            value = "core.leave.read",
            anyOf = {"core.leave_type.manage", "core.leave.apply"})
    public List<LeaveTypeResponse> getLeaveTypes(
            @RequestParam(name = "activeOn", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate activeOn) {
        UUID tenantId = TenantContext.require();
        return leaveTypeService.getLeaveTypes(tenantId, activeOn);
    }

    @PutMapping("/api/v1/leave-types/{id}")
    @RequiresAction("core.leave_type.manage")
    public LeaveTypeResponse updateLeaveType(@PathVariable("id") UUID id, @RequestBody LeaveTypeRequest request) {
        UUID tenantId = TenantContext.require();
        return leaveTypeService.updateLeaveType(tenantId, id, request);
    }

    @PutMapping("/api/v1/leave-types/{id}/policy")
    @RequiresAction("core.leave_type.manage")
    public LeavePolicyResponse configurePolicy(@PathVariable("id") UUID id, @RequestBody LeavePolicyRequest request) {
        UUID tenantId = TenantContext.require();
        return leaveTypeService.configurePolicy(tenantId, id, request);
    }

    @GetMapping("/api/v1/leave-types/{id}/policy/preview")
    @RequiresAction("core.leave_type.manage")
    public List<OverdrawnEmployee> previewPolicyChange(
            @PathVariable("id") UUID id,
            @RequestParam("annualDays") java.math.BigDecimal annualDays,
            @RequestParam(name = "accrualEnabled", required = false) Boolean accrualEnabled,
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        return leaveTypeService.previewPolicyChange(tenantId, id, annualDays, accrualEnabled, asOf);
    }

    @GetMapping("/api/v1/leave-types/eligible")
    @RequiresAction(
            value = "core.leave.apply",
            anyOf = {"core.leave.read", "core.leave_type.manage"})
    public List<LeaveTypeResponse> getEligibleLeaveTypes(
            @RequestParam("employeeId") UUID employeeId,
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        return leaveEligibilityService.getEligibleLeaveTypes(tenantId, employeeId, asOf);
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
