package com.infinevo.core.leave;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for manual leave allocations and mid-year policy recalculations (W-16.2, spec section 4 &amp; 13).
 */
public interface LeaveAllocationService {

    /**
     * Creates a leave allocation manually.
     */
    LeaveAllocationResponse createAllocation(UUID tenantId, LeaveAllocationRequest request);

    default LeaveAllocationResponse createAllocation(LeaveAllocationRequest request) {
        return createAllocation(TenantContext.require(), request);
    }

    /**
     * Reports which employees would become over-drawn if the policy's annual days were changed.
     */
    List<OverdrawnEmployee> previewMidYearPolicyImpact(
            UUID tenantId, UUID leaveTypeId, BigDecimal newAnnualDays, LocalDate asOf);

    /**
     * As above, for a change that may also switch accrual on or off. {@code newAccrualEnabled} is
     * the new policy's flag; {@code null} means the flag does not change.
     */
    List<OverdrawnEmployee> previewMidYearPolicyImpact(
            UUID tenantId, UUID leaveTypeId, BigDecimal newAnnualDays, Boolean newAccrualEnabled, LocalDate asOf);

    default List<OverdrawnEmployee> previewMidYearPolicyImpact(
            UUID leaveTypeId, BigDecimal newAnnualDays, LocalDate asOf) {
        return previewMidYearPolicyImpact(TenantContext.require(), leaveTypeId, newAnnualDays, asOf);
    }

    /**
     * Recalculates current-year allocations following a mid-year policy change (Decision 2).
     */
    int applyMidYearPolicyChange(UUID tenantId, UUID leaveTypeId, LeavePolicy newPolicy, LocalDate asOf);

    default int applyMidYearPolicyChange(UUID leaveTypeId, LeavePolicy newPolicy, LocalDate asOf) {
        return applyMidYearPolicyChange(TenantContext.require(), leaveTypeId, newPolicy, asOf);
    }
}
