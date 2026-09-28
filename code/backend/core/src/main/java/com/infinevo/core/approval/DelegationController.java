package com.infinevo.core.approval;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for approval delegations (W-15.3, spec section 4).
 */
@RestController
public class DelegationController {

    private final DelegationService delegationService;
    private final EmployeeService employeeService;

    public DelegationController(DelegationService delegationService, EmployeeService employeeService) {
        this.delegationService = Objects.requireNonNull(delegationService, "delegationService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @PostMapping("/api/v1/approval-delegations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.approval.delegate")
    public DelegationResponse createDelegation(@RequestBody DelegationCreateRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = currentCallerEmployee();
        return delegationService.createDelegation(tenantId, current.id(), request);
    }

    @GetMapping("/api/v1/approval-delegations")
    @RequiresAction("core.approval.delegate")
    public List<DelegationResponse> getDelegations(
            @RequestParam(name = "employeeId", required = false) UUID employeeId,
            @RequestParam(name = "activeOn", required = false) LocalDate activeOn) {
        UUID tenantId = TenantContext.require();
        return delegationService.getDelegations(tenantId, employeeId, activeOn);
    }

    @DeleteMapping("/api/v1/approval-delegations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresAction("core.approval.delegate")
    public void deleteDelegation(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse current = currentCallerEmployee();
        delegationService.deleteDelegation(tenantId, id, current.id());
    }

    private EmployeeResponse currentCallerEmployee() {
        Optional<EmployeeResponse> current = employeeService.currentEmployee();
        if (current.isEmpty()) {
            throw new AccessDeniedException("No employee profile found for caller");
        }
        return current.get();
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
