package com.infinevo.core.leave;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.org.ReportingLine;
import com.infinevo.core.org.ReportingLineRepository;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
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
}
