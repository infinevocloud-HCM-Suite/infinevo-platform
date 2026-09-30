package com.infinevo.core.leave;

import com.infinevo.shared.authz.RequiresAction;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        UUID tenantId = TenantContext.require();
        return leaveTypeService.previewPolicyChange(tenantId, id, annualDays, asOf);
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
}
