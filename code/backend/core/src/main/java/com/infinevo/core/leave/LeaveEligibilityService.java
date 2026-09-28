package com.infinevo.core.leave;

import com.infinevo.core.employee.Employee;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for checking employee eligibility for leave types and policies (W-16.1, spec section 4).
 * This is the seam W-16.3 plugs into for request-time validation.
 */
public interface LeaveEligibilityService {

    /**
     * Checks if the given employee is eligible for the specified leave type as of a given date.
     */
    boolean isEligible(Employee employee, LeaveType leaveType, LocalDate asOf);

    /**
     * Checks if the employee identified by ID is eligible for the leave type as of a given date.
     */
    boolean isEligible(UUID tenantId, UUID employeeId, UUID leaveTypeId, LocalDate asOf);

    default boolean isEligible(UUID employeeId, UUID leaveTypeId, LocalDate asOf) {
        return isEligible(TenantContext.require(), employeeId, leaveTypeId, asOf);
    }

    /**
     * Returns all active leave types for which the given employee is eligible as of the date.
     */
    List<LeaveTypeResponse> getEligibleLeaveTypes(UUID tenantId, UUID employeeId, LocalDate asOf);

    default List<LeaveTypeResponse> getEligibleLeaveTypes(UUID employeeId, LocalDate asOf) {
        return getEligibleLeaveTypes(TenantContext.require(), employeeId, asOf);
    }
}
