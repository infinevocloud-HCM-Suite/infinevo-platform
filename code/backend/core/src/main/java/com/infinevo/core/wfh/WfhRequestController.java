package com.infinevo.core.wfh;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wfh-requests")
public class WfhRequestController {

    private final WfhRequestService requestService;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;

    public WfhRequestController(
            WfhRequestService requestService, EmployeeService employeeService, PermissionService permissionService) {
        this.requestService = Objects.requireNonNull(requestService, "requestService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    @PostMapping("/on-behalf")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.wfh.manage")
    public WfhRequestResponse applyOnBehalf(@RequestBody ApplyWfhRequest request) {
        UUID tenantId = TenantContext.require();
        if (request.employeeId() == null) {
            throw new IllegalArgumentException("employeeId must be provided for on-behalf applications");
        }
        return requestService.applyOnBehalf(tenantId, request.employeeId(), request);
    }

    @GetMapping
    @RequiresAction(
            value = "core.wfh.read",
            anyOf = {"core.wfh.read_team"})
    public List<WfhRequestResponse> listRequests(@RequestParam(required = false) WfhRequestStatus status) {
        UUID tenantId = TenantContext.require();
        return requestService.listRequests(tenantId, status);
    }

    @GetMapping("/{id}")
    @RequiresAction(
            value = "core.wfh.read",
            anyOf = {"core.wfh.read_team", "core.wfh.read_own"})
    public WfhRequestResponse getRequest(@PathVariable UUID id) {
        UUID tenantId = TenantContext.require();
        WfhRequestResponse resp = requestService.getRequest(tenantId, id);
        if (permissionService.holds("core.wfh.read") || permissionService.holds("core.wfh.read_team")) {
            return resp;
        }
        // Caller only holds core.wfh.read_own
        UUID callerEmployeeId = requireCallerEmployeeId();
        if (!callerEmployeeId.equals(resp.employeeId())) {
            throw new AccessDeniedException("Cannot read another employee's WFH request");
        }
        return resp;
    }

    @PostMapping("/{id}/withdraw")
    @RequiresAction("core.wfh.apply")
    public WfhRequestResponse withdraw(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        UUID tenantId = TenantContext.require();
        UUID callerEmployeeId = authorizeOwnerOrManage(tenantId, id, "Cannot withdraw another employee's WFH request");
        String reason = body != null ? body.get("reason") : null;
        return requestService.withdraw(tenantId, id, callerEmployeeId, reason);
    }

    @PostMapping("/{id}/cancel")
    @RequiresAction("core.wfh.apply")
    public WfhRequestResponse cancel(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        UUID tenantId = TenantContext.require();
        UUID callerEmployeeId = authorizeOwnerOrManage(tenantId, id, "Cannot cancel another employee's WFH request");
        String reason = body != null ? body.get("reason") : null;
        return requestService.cancel(tenantId, id, callerEmployeeId, reason);
    }

    /**
     * Ownership check before any state change, as {@code LeaveRequestController.withdraw/cancel}.
     * A holder of {@code core.wfh.manage} acts on any request in the tenant; anyone else only on
     * their own, and a caller with no employee record on none. A missing id is still 404, raised
     * by the load. Returns the caller's employee id, null for a manager with no employee record.
     */
    private UUID authorizeOwnerOrManage(UUID tenantId, UUID requestId, String refusal) {
        if (permissionService.holds("core.wfh.manage")) {
            return employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
        }
        UUID callerEmployeeId = requireCallerEmployeeId();
        WfhRequestResponse existing = requestService.getRequest(tenantId, requestId);
        if (!callerEmployeeId.equals(existing.employeeId())) {
            throw new AccessDeniedException(refusal);
        }
        return callerEmployeeId;
    }

    private UUID requireCallerEmployeeId() {
        return employeeService
                .currentEmployee()
                .map(EmployeeResponse::id)
                .orElseThrow(() -> new AccessDeniedException("No employee profile found for caller"));
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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(ApiError.FORBIDDEN, e.getMessage(), traceId()));
    }

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
