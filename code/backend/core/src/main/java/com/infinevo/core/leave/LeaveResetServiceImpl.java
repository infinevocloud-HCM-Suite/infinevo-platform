package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final LeaveConsumptionRepository leaveConsumptionRepository;

    public LeaveResetServiceImpl(
            LeaveAllocationRepository leaveAllocationRepository,
            LeavePolicyRepository leavePolicyRepository,
            LeaveConsumptionRepository leaveConsumptionRepository) {
        this.leaveAllocationRepository =
                Objects.requireNonNull(leaveAllocationRepository, "leaveAllocationRepository must not be null");
        this.leavePolicyRepository =
                Objects.requireNonNull(leavePolicyRepository, "leavePolicyRepository must not be null");
        this.leaveConsumptionRepository = leaveConsumptionRepository;
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
            LeavePolicy policy =
                    leavePolicyRepository.findById(allocation.getPolicyId()).orElse(null);
            if (policy == null || !Boolean.TRUE.equals(policy.getResetEnabled())) {
                continue;
            }

            ResetFrequency freq = policy.getResetFrequency();
            if (freq == ResetFrequency.YEARLY && allocation.getLastResetOn() == null) {
                // If this is a new year's allocation, carry forward from the prior year's allocation
                LocalDate priorDate = allocation.getYearStartDate().minusDays(1);
                Optional<LeaveAllocation> priorOpt = leaveAllocationRepository
                        .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId,
                                allocation.getEmployeeId(),
                                allocation.getLeaveTypeId(),
                                priorDate,
                                priorDate);

                if (priorOpt.isPresent()) {
                    LeaveAllocation prior = priorOpt.get();
                    BigDecimal priorConsumed = BigDecimal.ZERO;
                    if (leaveConsumptionRepository != null && prior.getId() != null) {
                        BigDecimal sum =
                                leaveConsumptionRepository.sumConsumedDaysByAllocation(tenantId, prior.getId());
                        if (sum != null) {
                            priorConsumed = sum;
                        }
                    }
                    BigDecimal priorEntitlement =
                            prior.getEntitlementDays() != null ? prior.getEntitlementDays() : BigDecimal.ZERO;
                    BigDecimal priorAccrued = prior.getAccruedDays() != null ? prior.getAccruedDays() : BigDecimal.ZERO;
                    BigDecimal priorCarried =
                            prior.getCarriedForwardDays() != null ? prior.getCarriedForwardDays() : BigDecimal.ZERO;
                    BigDecimal priorUnused =
                            priorEntitlement.add(priorAccrued).add(priorCarried).subtract(priorConsumed);
                    if (priorUnused.compareTo(BigDecimal.ZERO) < 0) {
                        priorUnused = BigDecimal.ZERO;
                    }

                    if (Boolean.TRUE.equals(policy.getCarryForwardEnabled())) {
                        BigDecimal cap = policy.getCarryForwardCap();
                        BigDecimal toCarry = cap != null ? priorUnused.min(cap) : priorUnused;
                        allocation.setCarriedForwardDays(toCarry);
                        if (policy.getCarryForwardExpiresAfterMonths() != null
                                && policy.getCarryForwardExpiresAfterMonths() > 0) {
                            allocation.setCarryForwardExpiresOn(allocation
                                    .getYearStartDate()
                                    .plusMonths(policy.getCarryForwardExpiresAfterMonths()));
                        }
                    } else {
                        allocation.setCarriedForwardDays(BigDecimal.ZERO);
                        allocation.setCarryForwardExpiresOn(null);
                    }
                    allocation.setLastResetOn(evalDate);
                    leaveAllocationRepository.save(allocation);
                    count++;
                    continue;
                }
            }

            BigDecimal consumed = BigDecimal.ZERO;
            if (leaveConsumptionRepository != null && allocation.getId() != null) {
                BigDecimal sum = leaveConsumptionRepository.sumConsumedDaysByAllocation(tenantId, allocation.getId());
                if (sum != null) {
                    consumed = sum;
                }
            }
            if (resetAllocation(tenantId, allocation, evalDate, consumed)) {
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

        // Idempotence and boundary checks per frequency
        if (freq == ResetFrequency.YEARLY) {
            // Must be at or after allocation year end date
            if (asOf.isBefore(allocation.getYearEndDate())) {
                return false;
            }
            if (lastReset != null
                    && !lastReset.isBefore(allocation.getYearStartDate())
                    && !lastReset.isAfter(allocation.getYearEndDate())) {
                return false;
            }
        } else if (lastReset != null) {
            switch (freq) {
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
                default -> {}
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
