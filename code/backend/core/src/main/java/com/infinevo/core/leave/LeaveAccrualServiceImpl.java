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
 * Production implementation of {@link LeaveAccrualService} (W-16.2).
 */
@Service
@Transactional
public class LeaveAccrualServiceImpl implements LeaveAccrualService {

    private final LeaveAllocationRepository leaveAllocationRepository;
    private final LeavePolicyRepository leavePolicyRepository;

    public LeaveAccrualServiceImpl(
            LeaveAllocationRepository leaveAllocationRepository, LeavePolicyRepository leavePolicyRepository) {
        this.leaveAllocationRepository =
                Objects.requireNonNull(leaveAllocationRepository, "leaveAllocationRepository must not be null");
        this.leavePolicyRepository =
                Objects.requireNonNull(leavePolicyRepository, "leavePolicyRepository must not be null");
    }

    @Override
    public int accrueAll(UUID tenantId, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        LocalDate evalDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);

        List<LeaveAllocation> allocations =
                leaveAllocationRepository.findByTenantIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                        tenantId, evalDate, evalDate);

        int count = 0;
        for (LeaveAllocation allocation : allocations) {
            if (accrueAllocation(tenantId, allocation, evalDate)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean accrueAllocation(UUID tenantId, LeaveAllocation allocation, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(allocation, "allocation must not be null");
        Objects.requireNonNull(asOf, "asOf must not be null");

        // Verify allocation dates cover asOf
        if (asOf.isBefore(allocation.getYearStartDate()) || asOf.isAfter(allocation.getYearEndDate())) {
            return false;
        }

        LeavePolicy policy =
                leavePolicyRepository.findById(allocation.getPolicyId()).orElse(null);
        if (policy == null || !Boolean.TRUE.equals(policy.getAccrualEnabled())) {
            return false;
        }

        AccrualFrequency freq = policy.getAccrualFrequency();
        if (freq == null) {
            return false;
        }

        LocalDate lastAccrued = allocation.getLastAccruedOn();

        if (freq == AccrualFrequency.MONTHLY) {
            if (lastAccrued != null
                    && lastAccrued.getYear() == asOf.getYear()
                    && lastAccrued.getMonth() == asOf.getMonth()) {
                return false; // Idempotent: already accrued for this month
            }
            BigDecimal units = policy.getAccrualUnits();
            if (units == null || units.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
            BigDecimal current = allocation.getAccruedDays() != null ? allocation.getAccruedDays() : BigDecimal.ZERO;
            allocation.setAccruedDays(current.add(units));
            allocation.setLastAccruedOn(asOf);
            leaveAllocationRepository.save(allocation);
            return true;
        } else if (freq == AccrualFrequency.YEARLY) {
            if (lastAccrued != null && lastAccrued.getYear() == asOf.getYear()) {
                return false; // Idempotent: already accrued for this year
            }
            BigDecimal units =
                    policy.getAccrualUnits() != null ? policy.getAccrualUnits() : allocation.getEntitlementDays();
            if (units == null || units.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
            BigDecimal current = allocation.getAccruedDays() != null ? allocation.getAccruedDays() : BigDecimal.ZERO;
            allocation.setAccruedDays(current.add(units));
            allocation.setLastAccruedOn(asOf);
            leaveAllocationRepository.save(allocation);
            return true;
        }

        return false;
    }
}
