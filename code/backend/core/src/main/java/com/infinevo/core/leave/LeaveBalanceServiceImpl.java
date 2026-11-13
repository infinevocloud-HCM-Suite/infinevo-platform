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
    private final LeaveConsumptionRepository leaveConsumptionRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveBalanceServiceImpl(
            LeaveAllocationRepository allocationRepository, LeaveTypeRepository leaveTypeRepository) {
        this(allocationRepository, leaveTypeRepository, null, null);
    }

    public LeaveBalanceServiceImpl(
            LeaveAllocationRepository allocationRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeaveConsumptionRepository leaveConsumptionRepository) {
        this(allocationRepository, leaveTypeRepository, leaveConsumptionRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LeaveBalanceServiceImpl(
            LeaveAllocationRepository allocationRepository,
            LeaveTypeRepository leaveTypeRepository,
            LeaveConsumptionRepository leaveConsumptionRepository,
            LeaveRequestRepository leaveRequestRepository) {
        this.allocationRepository =
                Objects.requireNonNull(allocationRepository, "allocationRepository must not be null");
        this.leaveTypeRepository = Objects.requireNonNull(leaveTypeRepository, "leaveTypeRepository must not be null");
        this.leaveConsumptionRepository = leaveConsumptionRepository;
        this.leaveRequestRepository = leaveRequestRepository;
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

        // Sum real consumption from core.leave_consumption (W-16.4a)
        BigDecimal consumed = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (leaveConsumptionRepository != null && allocation.getId() != null) {
            BigDecimal sum = leaveConsumptionRepository.sumConsumedDaysByAllocation(tenantId, allocation.getId());
            if (sum != null) {
                consumed = sum.setScale(2, RoundingMode.HALF_UP);
            }
        }

        // Enforce carry-forward expiry: carried-forward days stop counting after carry_forward_expires_on (W-16.2)
        // Count only leave taken before the expiry date against the carried days (in LeaveDateUtils)
        carried = LeaveDateUtils.computeEffectiveCarriedForward(
                allocation, asOf, consumed, leaveConsumptionRepository, tenantId);

        // remaining = entitlement + accrued + carried_forward - consumed (W-16.2 spec section 3 & 7)
        BigDecimal remaining = entitlement.add(accrued).add(carried).subtract(consumed);

        // Sum pending leave requests for this allocation year (D-138)
        BigDecimal pending = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (leaveRequestRepository != null && allocation.getLeaveTypeId() != null) {
            List<LeaveRequest> pendingRequests =
                    leaveRequestRepository.findByTenantIdAndEmployeeIdAndLeaveTypeIdAndStatus(
                            tenantId,
                            allocation.getEmployeeId(),
                            allocation.getLeaveTypeId(),
                            LeaveRequestStatus.PENDING);
            for (LeaveRequest req : pendingRequests) {
                if (req.getWorkingDays() != null
                        && (allocation.getYearStartDate() == null
                                || !req.getFromDate().isBefore(allocation.getYearStartDate()))
                        && (allocation.getYearEndDate() == null
                                || !req.getToDate().isAfter(allocation.getYearEndDate()))) {
                    pending = pending.add(req.getWorkingDays());
                }
            }
            pending = pending.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal available = remaining.subtract(pending);
        if (available.compareTo(BigDecimal.ZERO) < 0) {
            available = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // An ended or switched-off leave type is history: its days still show, marked expired (W-77.1).
        // Judged on the earlier of asOf and today: Allocations asks as of 31 December, and a type
        // that is still running today has not ended yet.
        LocalDate validTo = type != null ? type.getValidTo() : null;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate expiryDate = asOf.isAfter(today) ? today : asOf;
        boolean expired = type == null || !type.isActive() || (validTo != null && validTo.isBefore(expiryDate));

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
                allocation.getCarryForwardExpiresOn(),
                validTo,
                expired,
                pending,
                available);
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
