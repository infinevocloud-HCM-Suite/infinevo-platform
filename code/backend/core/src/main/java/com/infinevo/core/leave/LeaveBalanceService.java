package com.infinevo.core.leave;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for calculating employee leave balances (W-16.2, spec section 4 &amp; 6).
 * This is the read side W-16.3 uses.
 */
public interface LeaveBalanceService {

    /**
     * Returns balances for all leave types allocated to the employee as of the date.
     */
    List<LeaveBalanceResponse> getBalancesForEmployee(UUID tenantId, UUID employeeId, LocalDate asOf);

    default List<LeaveBalanceResponse> getBalancesForEmployee(UUID employeeId, LocalDate asOf) {
        return getBalancesForEmployee(TenantContext.require(), employeeId, asOf);
    }

    /**
     * Returns the balance for a specific employee and leave type as of the date.
     */
    Optional<LeaveBalanceResponse> getBalance(UUID tenantId, UUID employeeId, UUID leaveTypeId, LocalDate asOf);

    default Optional<LeaveBalanceResponse> getBalance(UUID employeeId, UUID leaveTypeId, LocalDate asOf) {
        return getBalance(TenantContext.require(), employeeId, leaveTypeId, asOf);
    }
}
