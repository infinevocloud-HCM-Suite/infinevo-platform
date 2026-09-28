package com.infinevo.core.leave;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing leave types and configuring leave policies (W-16.1, spec section 4).
 */
public interface LeaveTypeService {

    /**
     * Creates a new leave type for the tenant and optionally its initial policy.
     */
    LeaveTypeResponse createLeaveType(UUID tenantId, LeaveTypeRequest request);

    default LeaveTypeResponse createLeaveType(LeaveTypeRequest request) {
        return createLeaveType(TenantContext.require(), request);
    }

    /**
     * Updates an existing leave type's identity details.
     */
    LeaveTypeResponse updateLeaveType(UUID tenantId, UUID id, LeaveTypeRequest request);

    default LeaveTypeResponse updateLeaveType(UUID id, LeaveTypeRequest request) {
        return updateLeaveType(TenantContext.require(), id, request);
    }

    /**
     * Returns all leave types for the tenant, optionally filtering by active status on the given date,
     * each populated with its effective policy.
     */
    List<LeaveTypeResponse> getLeaveTypes(UUID tenantId, LocalDate activeOn);

    default List<LeaveTypeResponse> getLeaveTypes(LocalDate activeOn) {
        return getLeaveTypes(TenantContext.require(), activeOn);
    }

    /**
     * Returns a leave type by ID with its policy effective as of the given date.
     */
    Optional<LeaveTypeResponse> getLeaveType(UUID tenantId, UUID id, LocalDate asOf);

    default Optional<LeaveTypeResponse> getLeaveType(UUID id, LocalDate asOf) {
        return getLeaveType(TenantContext.require(), id, asOf);
    }

    /**
     * Configures a new leave policy version for the given leave type.
     */
    LeavePolicyResponse configurePolicy(UUID tenantId, UUID leaveTypeId, LeavePolicyRequest request);

    default LeavePolicyResponse configurePolicy(UUID leaveTypeId, LeavePolicyRequest request) {
        return configurePolicy(TenantContext.require(), leaveTypeId, request);
    }

    /**
     * Sets / configures a policy matrix for the given leave type.
     */
    default LeavePolicyResponse setPolicy(UUID leaveTypeId, LeavePolicyRequest request) {
        return configurePolicy(TenantContext.require(), leaveTypeId, request);
    }

    /**
     * Returns the leave policy in force on the given date for a leave type.
     */
    Optional<LeavePolicyResponse> getEffectivePolicy(UUID tenantId, UUID leaveTypeId, LocalDate asOf);

    default Optional<LeavePolicyResponse> getEffectivePolicy(UUID leaveTypeId, LocalDate asOf) {
        return getEffectivePolicy(TenantContext.require(), leaveTypeId, asOf);
    }
}
