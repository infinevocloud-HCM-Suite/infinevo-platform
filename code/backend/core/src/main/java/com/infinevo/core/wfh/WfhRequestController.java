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
        return requestService.getRequest(tenantId, id);
    }

    @PostMapping("/{id}/withdraw")
    @RequiresAction("core.wfh.apply")
    public WfhRequestResponse withdraw(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        UUID tenantId = TenantContext.require();
        UUID callerEmployeeId = currentCallerEmployeeId();
        String reason = body != null ? body.get("reason") : null;
        return requestService.withdraw(tenantId, id, callerEmployeeId, reason);
    }

    @PostMapping("/{id}/cancel")
    @RequiresAction("core.wfh.apply")
    public WfhRequestResponse cancel(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        UUID tenantId = TenantContext.require();
        UUID callerEmployeeId = currentCallerEmployeeId();
        String reason = body != null ? body.get("reason") : null;
        return requestService.cancel(tenantId, id, callerEmployeeId, reason);
    }

    private UUID currentCallerEmployeeId() {
        return employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
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
