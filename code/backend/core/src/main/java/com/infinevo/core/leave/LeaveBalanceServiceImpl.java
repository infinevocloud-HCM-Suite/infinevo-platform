package com.infinevo.core.leave;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of {@link LeaveBalanceService} (W-16.2).
 */
@Service
@Transactional(readOnly = true)
public class LeaveBalanceServiceImpl implements LeaveBalanceService {

    private final LeaveAllocationRepository allocationRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveBalanceServiceImpl(
            LeaveAllocationRepository allocationRepository, LeaveTypeRepository leaveTypeRepository) {
        this.allocationRepository =
                Objects.requireNonNull(allocationRepository, "allocationRepository must not be null");
        this.leaveTypeRepository = Objects.requireNonNull(leaveTypeRepository, "leaveTypeRepository must not be null");
    }

    @Override
    public List<LeaveBalanceResponse> getBalancesForEmployee(UUID tenantId, UUID employeeId, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");

        LocalDate evalDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);

        List<LeaveAllocation> allocations =
                allocationRepository
                        .findByTenantIdAndEmployeeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, employeeId, evalDate, evalDate);

        List<LeaveBalanceResponse> responses = new ArrayList<>(allocations.size());
        for (LeaveAllocation allocation : allocations) {
            responses.add(computeBalance(tenantId, allocation, evalDate));
        }
        return responses;
    }

    @Override
    public Optional<LeaveBalanceResponse> getBalance(UUID tenantId, UUID employeeId, UUID leaveTypeId, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employeeId, "employeeId must not be null");
        Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");

        LocalDate evalDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);

        return allocationRepository
                .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                        tenantId, employeeId, leaveTypeId, evalDate, evalDate)
                .map(allocation -> computeBalance(tenantId, allocation, evalDate));
    }

    private LeaveBalanceResponse computeBalance(UUID tenantId, LeaveAllocation allocation, LocalDate asOf) {
        LeaveType type = leaveTypeRepository
                .findByTenantIdAndId(tenantId, allocation.getLeaveTypeId())
                .orElse(null);

        String code = type != null ? type.getCode() : "";
        String name = type != null ? type.getName() : "";

        BigDecimal entitlement = allocation.getEntitlementDays() != null
                ? allocation.getEntitlementDays()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal accrued = allocation.getAccruedDays() != null
                ? allocation.getAccruedDays()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal carried = allocation.getCarriedForwardDays() != null
                ? allocation.getCarriedForwardDays()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // Enforce carry-forward expiry: carried-forward days stop counting after carry_forward_expires_on (W-16.2 spec
        // section 2 & 7)
        if (allocation.getCarryForwardExpiresOn() != null && asOf.isAfter(allocation.getCarryForwardExpiresOn())) {
            carried = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // Consumption is 0 until W-16.4a provides core.leave_consumption
        BigDecimal consumed = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // remaining = entitlement + accrued + carried_forward - consumed (W-16.2 spec section 3 & 7)
        BigDecimal remaining = entitlement.add(accrued).add(carried).subtract(consumed);

        return new LeaveBalanceResponse(
                allocation.getEmployeeId(),
                allocation.getLeaveTypeId(),
                code,
                name,
                allocation.getLeaveYear(),
                entitlement,
                accrued,
                carried,
                consumed,
                remaining,
                allocation.getCarryForwardExpiresOn());
    }

    @Override
    public List<LeaveBalanceResponse> getBalancesForEmployee(UUID employeeId, LocalDate asOf) {
        return getBalancesForEmployee(TenantContext.require(), employeeId, asOf);
    }

    @Override
    public Optional<LeaveBalanceResponse> getBalance(UUID employeeId, UUID leaveTypeId, LocalDate asOf) {
        return getBalance(TenantContext.require(), employeeId, leaveTypeId, asOf);
    }
}
