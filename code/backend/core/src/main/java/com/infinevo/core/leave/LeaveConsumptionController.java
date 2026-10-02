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
import java.time.YearMonth;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for viewing leave consumption and loss-of-pay records (W-16.4a, spec section 4).
 */
@RestController
public class LeaveConsumptionController {

    private final LeaveConsumptionService leaveConsumptionService;
    private final EmployeeService employeeService;
    private final PermissionService permissionService;
    private final ReportingLineRepository reportingLineRepository;

    public LeaveConsumptionController(
            LeaveConsumptionService leaveConsumptionService,
            EmployeeService employeeService,
            PermissionService permissionService,
            ReportingLineRepository reportingLineRepository) {
        this.leaveConsumptionService =
                Objects.requireNonNull(leaveConsumptionService, "leaveConsumptionService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.reportingLineRepository =
                Objects.requireNonNull(reportingLineRepository, "reportingLineRepository must not be null");
    }

    @GetMapping("/api/v1/employees/{id}/leave-consumption")
    @RequiresAction(
            value = "core.leave.read",
            anyOf = {"core.leave.read_own", "core.leave.read_team"})
    public List<LeaveConsumptionResponse> getConsumption(
            @PathVariable("id") UUID employeeId, @RequestParam(value = "year", required = false) Integer year) {
        assertCanReadEmployee(employeeId);
        return leaveConsumptionService.getConsumption(employeeId, year);
    }

    @GetMapping("/api/v1/employees/{id}/lop")
    @RequiresAction(
            value = "core.leave.read",
            anyOf = {"core.leave.read_own", "core.leave.read_team"})
    public LopResponse getLop(@PathVariable("id") UUID employeeId, @RequestParam(value = "period") String period) {
        assertCanReadEmployee(employeeId);
        YearMonth ym = YearMonth.parse(period);
        return leaveConsumptionService.getLop(employeeId, ym);
    }

    private void assertCanReadEmployee(UUID targetEmployeeId) {
        if (permissionService.holds("core.leave.read")) {
            return;
        }

        UUID tenantId = TenantContext.require();
        EmployeeResponse caller = employeeService
                .currentEmployee()
                .orElseThrow(() -> new AccessDeniedException("No employee profile linked to current user"));

        if (caller.id().equals(targetEmployeeId)) {
            return;
        }

        if (permissionService.holds("core.leave.read_team")) {
            List<ReportingLine> directReports =
                    reportingLineRepository.findDirectReports(tenantId, caller.id(), LocalDate.now());
            boolean isReport =
                    directReports.stream().anyMatch(l -> l.getEmployee().getId().equals(targetEmployeeId));
            if (isReport) {
                return;
            }
        }

        throw new AccessDeniedException("Access denied to employee leave data");
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
