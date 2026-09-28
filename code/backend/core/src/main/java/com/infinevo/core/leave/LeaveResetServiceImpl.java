package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of {@link LeaveResetService} (W-16.2).
 */
@Service
@Transactional
public class LeaveResetServiceImpl implements LeaveResetService {

    private final LeaveAllocationRepository leaveAllocationRepository;
    private final LeavePolicyRepository leavePolicyRepository;

    public LeaveResetServiceImpl(
            LeaveAllocationRepository leaveAllocationRepository, LeavePolicyRepository leavePolicyRepository) {
        this.leaveAllocationRepository =
                Objects.requireNonNull(leaveAllocationRepository, "leaveAllocationRepository must not be null");
        this.leavePolicyRepository =
                Objects.requireNonNull(leavePolicyRepository, "leavePolicyRepository must not be null");
    }

    @Override
    public int resetAll(UUID tenantId, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        LocalDate evalDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);

        List<LeaveAllocation> allocations =
                leaveAllocationRepository.findByTenantIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                        tenantId, evalDate, evalDate);

        int count = 0;
        for (LeaveAllocation allocation : allocations) {
            if (resetAllocation(tenantId, allocation, evalDate, BigDecimal.ZERO)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean resetAllocation(UUID tenantId, LeaveAllocation allocation, LocalDate asOf, BigDecimal consumedDays) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(allocation, "allocation must not be null");
        Objects.requireNonNull(asOf, "asOf must not be null");

        LeavePolicy policy =
                leavePolicyRepository.findById(allocation.getPolicyId()).orElse(null);
        if (policy == null || !Boolean.TRUE.equals(policy.getResetEnabled())) {
            return false;
        }

        ResetFrequency freq = policy.getResetFrequency();
        if (freq == null) {
            return false;
        }

        LocalDate lastReset = allocation.getLastResetOn();

        // Idempotence checks per frequency boundary
        if (lastReset != null) {
            switch (freq) {
                case YEARLY -> {
                    if (lastReset.getYear() == asOf.getYear()) {
                        return false;
                    }
                }
                case MONTHLY -> {
                    if (lastReset.getYear() == asOf.getYear() && lastReset.getMonth() == asOf.getMonth()) {
                        return false;
                    }
                }
                case QUARTERLY -> {
                    int lastQ = (lastReset.getMonthValue() - 1) / 3;
                    int curQ = (asOf.getMonthValue() - 1) / 3;
                    if (lastReset.getYear() == asOf.getYear() && lastQ == curQ) {
                        return false;
                    }
                }
                case HALF_YEARLY -> {
                    boolean lastH1 = lastReset.getMonthValue() <= 6;
                    boolean curH1 = asOf.getMonthValue() <= 6;
                    if (lastReset.getYear() == asOf.getYear() && lastH1 == curH1) {
                        return false;
                    }
                }
            }
        }

        BigDecimal consumed = consumedDays != null ? consumedDays : BigDecimal.ZERO;
        BigDecimal entitlement =
                allocation.getEntitlementDays() != null ? allocation.getEntitlementDays() : BigDecimal.ZERO;
        BigDecimal accrued = allocation.getAccruedDays() != null ? allocation.getAccruedDays() : BigDecimal.ZERO;
        BigDecimal carried =
                allocation.getCarriedForwardDays() != null ? allocation.getCarriedForwardDays() : BigDecimal.ZERO;

        BigDecimal totalAvailable = entitlement.add(accrued).add(carried);
        BigDecimal unused = totalAvailable.subtract(consumed);
        if (unused.compareTo(BigDecimal.ZERO) < 0) {
            unused = BigDecimal.ZERO;
        }

        if (Boolean.TRUE.equals(policy.getCarryForwardEnabled())) {
            BigDecimal cap = policy.getCarryForwardCap();
            BigDecimal toCarry = cap != null ? unused.min(cap) : unused;
            allocation.setCarriedForwardDays(toCarry);

            if (policy.getCarryForwardExpiresAfterMonths() != null && policy.getCarryForwardExpiresAfterMonths() > 0) {
                allocation.setCarryForwardExpiresOn(asOf.plusMonths(policy.getCarryForwardExpiresAfterMonths()));
            } else {
                allocation.setCarryForwardExpiresOn(null);
            }
        } else {
            // Unused balance drops at boundary
            allocation.setCarriedForwardDays(BigDecimal.ZERO);
            allocation.setCarryForwardExpiresOn(null);
        }

        allocation.setAccruedDays(BigDecimal.ZERO);
        allocation.setLastResetOn(asOf);
        leaveAllocationRepository.save(allocation);
        return true;
    }
}
