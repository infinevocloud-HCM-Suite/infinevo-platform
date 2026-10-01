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
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for leave requests and lifecycle transitions (W-16.3, spec section 4).
 */
@RestController
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;
    private final ReportingLineRepository reportingLineRepository;

    public LeaveRequestController(
            LeaveRequestService leaveRequestService,
            EmployeeService employeeService,
            PermissionService permissionService,
            ReportingLineRepository reportingLineRepository) {
        this.leaveRequestService = Objects.requireNonNull(leaveRequestService, "leaveRequestService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.reportingLineRepository =
                Objects.requireNonNull(reportingLineRepository, "reportingLineRepository must not be null");
    }

    @PostMapping("/api/v1/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.leave.apply")
    public LeaveRequestResponse createRequest(@RequestBody LeaveApplyRequest request) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse caller = currentCallerEmployee();
        return leaveRequestService.createRequest(tenantId, caller.id(), request);
    }

    @PostMapping("/api/v1/leave-requests/on-behalf")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresAction("core.leave.manage")
    public LeaveRequestResponse createOnBehalf(@RequestBody LeaveOnBehalfRequest request) {
        UUID tenantId = TenantContext.require();
        return leaveRequestService.createOnBehalf(tenantId, request);
    }

    @GetMapping("/api/v1/leave-requests")
    @RequiresAction(
            value = "core.leave.read",
            anyOf = {"core.leave.read_own", "core.leave.read_team"})
    public Page<LeaveRequestResponse> searchRequests(
            @RequestParam(name = "employeeId", required = false) UUID employeeId,
            @RequestParam(name = "status", required = false) LeaveRequestStatus status,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate,
            @PageableDefault(size = 25) Pageable pageable) {
        UUID tenantId = TenantContext.require();

        if (permissionService.holds("core.leave.read")) {
            return leaveRequestService.searchRequests(tenantId, employeeId, status, fromDate, toDate, pageable);
        }

        EmployeeResponse caller = currentCallerEmployee();
        if (permissionService.holds("core.leave.read_team")) {
            List<UUID> allowedIds = new ArrayList<>();
            allowedIds.add(caller.id());
            List<ReportingLine> directReports =
                    reportingLineRepository.findDirectReports(tenantId, caller.id(), LocalDate.now());
            for (ReportingLine line : directReports) {
                allowedIds.add(line.getEmployee().getId());
            }
            if (employeeId != null) {
                if (!allowedIds.contains(employeeId)) {
                    return Page.empty(pageable);
                }
                return leaveRequestService.searchRequests(tenantId, employeeId, status, fromDate, toDate, pageable);
            }
            return leaveRequestService.searchRequestsForEmployees(
                    tenantId, allowedIds, status, fromDate, toDate, pageable);
        }

        // Caller only holds core.leave.read_own
        if (employeeId != null && !caller.id().equals(employeeId)) {
            return Page.empty(pageable);
        }
        return leaveRequestService.searchRequests(tenantId, caller.id(), status, fromDate, toDate, pageable);
    }

    @GetMapping("/api/v1/me/leave-requests")
    @RequiresAction("core.leave.read_own")
    public Page<LeaveRequestResponse> myRequests(
            @RequestParam(name = "status", required = false) LeaveRequestStatus status,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate,
            @PageableDefault(size = 25) Pageable pageable) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse caller = currentCallerEmployee();
        return leaveRequestService.searchRequests(tenantId, caller.id(), status, fromDate, toDate, pageable);
    }

    @GetMapping("/api/v1/leave-requests/{id}")
    @RequiresAction(
            value = "core.leave.read",
            anyOf = {"core.leave.read_own", "core.leave.read_team"})
    public LeaveRequestResponse getRequest(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        LeaveRequestResponse resp = leaveRequestService.getRequest(tenantId, id);

        if (permissionService.holds("core.leave.read")) {
            return resp;
        }

        EmployeeResponse caller = currentCallerEmployee();
        if (permissionService.holds("core.leave.read_team")) {
            if (caller.id().equals(resp.employeeId())) {
                return resp;
            }
            List<ReportingLine> directReports =
                    reportingLineRepository.findDirectReports(tenantId, caller.id(), LocalDate.now());
            boolean isReport =
                    directReports.stream().anyMatch(l -> l.getEmployee().getId().equals(resp.employeeId()));
            if (isReport) {
                return resp;
            }
            throw new AccessDeniedException("Access denied to leave request");
        }

        // Caller only holds core.leave.read_own
        if (!caller.id().equals(resp.employeeId())) {
            throw new AccessDeniedException("Access denied to leave request");
        }
        return resp;
    }

    @PostMapping("/api/v1/leave-requests/{id}/submit")
    @RequiresAction("core.leave.apply")
    public LeaveRequestResponse submit(@PathVariable("id") UUID id) {
        UUID tenantId = TenantContext.require();
        EmployeeResponse caller = currentCallerEmployee();
        LeaveRequestResponse existing = leaveRequestService.getRequest(tenantId, id);
        if (!caller.id().equals(existing.employeeId())) {
            throw new AccessDeniedException("Cannot submit another employee's leave request");
        }
        return leaveRequestService.submit(tenantId, id, caller.id());
    }

    @PostMapping("/api/v1/leave-requests/{id}/withdraw")
    @RequiresAction(value = "core.leave.apply", anyOf = "core.leave.manage")
    public LeaveRequestResponse withdraw(
            @PathVariable("id") UUID id, @RequestBody(required = false) LeaveReasonRequest reasonRequest) {
        UUID tenantId = TenantContext.require();
        UUID callerEmployeeId = null;
        if (!permissionService.holds("core.leave.manage")) {
            EmployeeResponse caller = currentCallerEmployee();
            callerEmployeeId = caller.id();
            LeaveRequestResponse existing = leaveRequestService.getRequest(tenantId, id);
            if (!caller.id().equals(existing.employeeId())) {
                throw new AccessDeniedException("Cannot withdraw another employee's leave request");
            }
        } else {
            callerEmployeeId =
                    employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
        }
        String reason = reasonRequest != null ? reasonRequest.reason() : null;
        return leaveRequestService.withdraw(tenantId, id, callerEmployeeId, reason);
    }

    @PostMapping("/api/v1/leave-requests/{id}/cancel")
    @RequiresAction(value = "core.leave.apply", anyOf = "core.leave.manage")
    public LeaveRequestResponse cancel(
            @PathVariable("id") UUID id, @RequestBody(required = false) LeaveReasonRequest reasonRequest) {
        UUID tenantId = TenantContext.require();
        UUID callerEmployeeId = null;
        if (!permissionService.holds("core.leave.manage")) {
            EmployeeResponse caller = currentCallerEmployee();
            callerEmployeeId = caller.id();
            LeaveRequestResponse existing = leaveRequestService.getRequest(tenantId, id);
            if (!caller.id().equals(existing.employeeId())) {
                throw new AccessDeniedException("Cannot cancel another employee's leave request");
            }
        } else {
            callerEmployeeId =
                    employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
        }
        String reason = reasonRequest != null ? reasonRequest.reason() : null;
        return leaveRequestService.cancel(tenantId, id, callerEmployeeId, reason);
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

    private static String traceId() {
        String traceId = MDC.get(MdcLoggingContext.CORRELATION_ID_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : traceId;
    }
}
