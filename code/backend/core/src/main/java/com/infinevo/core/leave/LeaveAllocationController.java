package com.infinevo.core.leave;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for leave allocations, balances, and accrual triggers (W-16.2, spec section 4).
 */
@RestController
public class LeaveAllocationController {

    private final LeaveAllocationService allocationService;
    private final LeaveBalanceService balanceService;
    private final LeaveAccrualService accrualService;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;
    private final ReportingLineRepository reportingLineRepository;

    public LeaveAllocationController(
            LeaveAllocationService allocationService,
            LeaveBalanceService balanceService,
            LeaveAccrualService accrualService,
            EmployeeService employeeService,
            PermissionService permissionService,
            ReportingLineRepository reportingLineRepository) {
        this.allocationService = Objects.requireNonNull(allocationService, "allocationService must not be null");
        this.balanceService = Objects.requireNonNull(balanceService, "balanceService must not be null");
        this.accrualService = Objects.requireNonNull(accrualService, "accrualService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.reportingLineRepository =
                Objects.requireNonNull(reportingLineRepository, "reportingLineRepository must not be null");
    }

    @GetMapping("/api/v1/employees/{id}/leave-balances")
    @RequiresAction(
            value = "core.leave.read",
            anyOf = {"core.leave.read_own", "core.leave.read_team"})
    public List<LeaveBalanceResponse> getLeaveBalances(
            @PathVariable("id") UUID employeeId,
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        UUID tenantId = TenantContext.require();

        if (!permissionService.holds("core.leave.read")) {
            EmployeeResponse caller = employeeService
                    .currentEmployee()
                    .orElseThrow(() -> new AccessDeniedException("No employee linked to current user"));

            if (permissionService.holds("core.leave.read_team")) {
                if (!caller.id().equals(employeeId)) {
                    List<ReportingLine> directReports = reportingLineRepository.findDirectReports(
                            tenantId, caller.id(), LocalDate.now(ZoneOffset.UTC));
                    boolean isReport = directReports.stream()
                            .anyMatch(l -> l.getEmployee().getId().equals(employeeId));
                    if (!isReport) {
                        throw new AccessDeniedException("Access denied to employee leave balances");
                    }
                }
            } else if (permissionService.holds("core.leave.read_own")) {
                if (!caller.id().equals(employeeId)) {
                    throw new AccessDeniedException("Cannot view another employee's leave balances");
                }
            } else {
                throw new AccessDeniedException("Access denied to leave balances");
            }
        }

        return balanceService.getBalancesForEmployee(tenantId, employeeId, asOf);
    }

    @PostMapping("/api/v1/leave-allocations")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.leave_balance.manage")
    public LeaveAllocationResponse createAllocation(@RequestBody LeaveAllocationRequest request) {
        UUID tenantId = TenantContext.require();
        return allocationService.createAllocation(tenantId, request);
    }

    @PostMapping("/api/v1/leave-allocations/accrue")
    @RequiresAction("core.leave_balance.manage")
    public Map<String, Object> triggerAccrual(
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        int accruedCount = accrualService.accrueAll(tenantId, asOf);
        return Map.of("accruedCount", accruedCount);
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
