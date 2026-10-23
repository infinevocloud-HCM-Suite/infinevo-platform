package com.infinevo.core.wfh;

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
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MyWfhController {

    private final WfhRequestService requestService;
    private final WfhBalanceService balanceService;
    private final EmployeeService employeeService;

    public MyWfhController(
            WfhRequestService requestService, WfhBalanceService balanceService, EmployeeService employeeService) {
        this.requestService = Objects.requireNonNull(requestService, "requestService must not be null");
        this.balanceService = Objects.requireNonNull(balanceService, "balanceService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @PostMapping("/wfh-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.wfh.apply")
    public WfhRequestResponse apply(@RequestBody ApplyWfhRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse caller = requireCallerEmployee();
        return requestService.apply(tenantId, caller.id(), request);
    }

    @GetMapping("/wfh-requests")
    @RequiresAction("core.wfh.read_own")
    public List<WfhRequestResponse> listMyRequests() {
        UUID tenantId = TenantContext.require();
        EmployeeResponse caller = requireCallerEmployee();
        return requestService.listEmployeeRequests(tenantId, caller.id());
    }

    @GetMapping("/wfh-balance")
    @RequiresAction("core.wfh.read_own")
    public WfhBalanceSummaryResponse getMyBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse caller = requireCallerEmployee();
        LocalDate asOfDate = asOf != null ? asOf : LocalDate.now();
        return balanceService.getBalanceSummary(tenantId, caller.id(), asOfDate);
    }

    private EmployeeResponse requireCallerEmployee() {
        return employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile found for authenticated user"));
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
