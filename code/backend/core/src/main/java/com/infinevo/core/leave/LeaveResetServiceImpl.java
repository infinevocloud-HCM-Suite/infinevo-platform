package com.infinevo.core.leave;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    public LeaveResetServiceImpl(
            LeaveAllocationRepository leaveAllocationRepository,
            LeavePolicyRepository leavePolicyRepository,
            LeaveConsumptionRepository leaveConsumptionRepository) {
        this(leaveAllocationRepository, leavePolicyRepository, leaveConsumptionRepository, null);
    }

    @Autowired
    public LeaveResetServiceImpl(
            LeaveAllocationRepository leaveAllocationRepository,
            LeavePolicyRepository leavePolicyRepository,
            LeaveConsumptionRepository leaveConsumptionRepository,
            @Autowired(required = false) JdbcTemplate jdbcTemplate) {
        this.leaveAllocationRepository =
                Objects.requireNonNull(leaveAllocationRepository, "leaveAllocationRepository must not be null");
        this.leavePolicyRepository =
                Objects.requireNonNull(leavePolicyRepository, "leavePolicyRepository must not be null");
        this.leaveConsumptionRepository = leaveConsumptionRepository;
        this.jdbcTemplate = jdbcTemplate;
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
                    // Count only leave taken before the expiry date against carried days (W-16.2)
                    BigDecimal priorCarried = LeaveDateUtils.computeEffectiveCarriedForward(
                            prior, evalDate, priorConsumed, leaveConsumptionRepository, tenantId);
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
                    // Write ONLY to the new year's row; old year's row (prior) is left alone (W-16.2)
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

        int startMonth = LeaveDateUtils.getTenantLeaveYearStartMonth(jdbcTemplate, tenantId);

        // Never reset a balance in the same period it was created (W-16.2)
        LocalDate createdDate = allocation.getCreatedAt() != null
                ? LocalDate.ofInstant(allocation.getCreatedAt(), ZoneOffset.UTC)
                : allocation.getYearStartDate();
        int createdPeriod = LeaveDateUtils.getPeriodIndex(createdDate, startMonth, freq);
        int asOfPeriod = LeaveDateUtils.getPeriodIndex(asOf, startMonth, freq);

        if (asOfPeriod <= createdPeriod) {
            return false;
        }

        LocalDate lastReset = allocation.getLastResetOn();

        // Idempotence and boundary checks per frequency relative to tenant's leave year start
        if (freq == ResetFrequency.YEARLY) {
            // Must be at or after allocation year end date
            if (asOf.isBefore(allocation.getYearEndDate())) {
                return false;
            }
            // For YEARLY at year end, only the new year's row is written; old year's row is left alone (W-16.2)
            LocalDate nextYearDate = allocation.getYearEndDate().plusDays(1);
            Optional<LeaveAllocation> nextOpt = leaveAllocationRepository
                    .findFirstByTenantIdAndEmployeeIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                            tenantId,
                            allocation.getEmployeeId(),
                            allocation.getLeaveTypeId(),
                            nextYearDate,
                            nextYearDate);
            if (nextOpt.isPresent()) {
                LeaveAllocation nextAlloc = nextOpt.get();
                if (nextAlloc.getLastResetOn() == null) {
                    BigDecimal consumed = consumedDays != null ? consumedDays : BigDecimal.ZERO;
                    BigDecimal entitlement =
                            allocation.getEntitlementDays() != null ? allocation.getEntitlementDays() : BigDecimal.ZERO;
                    BigDecimal accrued =
                            allocation.getAccruedDays() != null ? allocation.getAccruedDays() : BigDecimal.ZERO;
                    BigDecimal carried = LeaveDateUtils.computeEffectiveCarriedForward(
                            allocation, asOf, consumed, leaveConsumptionRepository, tenantId);
                    BigDecimal totalAvailable = entitlement.add(accrued).add(carried);
                    BigDecimal unused = totalAvailable.subtract(consumed).max(BigDecimal.ZERO);

                    BigDecimal toCarry = BigDecimal.ZERO;
                    if (Boolean.TRUE.equals(policy.getCarryForwardEnabled())) {
                        BigDecimal cap = policy.getCarryForwardCap();
                        toCarry = cap != null ? unused.min(cap) : unused;
                        if (policy.getCarryForwardExpiresAfterMonths() != null
                                && policy.getCarryForwardExpiresAfterMonths() > 0) {
                            nextAlloc.setCarryForwardExpiresOn(nextAlloc
                                    .getYearStartDate()
                                    .plusMonths(policy.getCarryForwardExpiresAfterMonths()));
                        } else {
                            nextAlloc.setCarryForwardExpiresOn(null);
                        }
                    } else {
                        nextAlloc.setCarryForwardExpiresOn(null);
                    }
                    nextAlloc.setCarriedForwardDays(toCarry);
                    nextAlloc.setLastResetOn(asOf);
                    leaveAllocationRepository.save(nextAlloc);
                    return true;
                }
            }
            return false;
        } else if (lastReset != null) {
            int lastResetPeriod = LeaveDateUtils.getPeriodIndex(lastReset, startMonth, freq);
            if (asOfPeriod <= lastResetPeriod) {
                return false;
            }
        }

        BigDecimal consumed = consumedDays != null ? consumedDays : BigDecimal.ZERO;
        BigDecimal entitlement =
                allocation.getEntitlementDays() != null ? allocation.getEntitlementDays() : BigDecimal.ZERO;
        BigDecimal accrued = allocation.getAccruedDays() != null ? allocation.getAccruedDays() : BigDecimal.ZERO;
        BigDecimal carried = LeaveDateUtils.computeEffectiveCarriedForward(
                allocation, asOf, consumed, leaveConsumptionRepository, tenantId);

        BigDecimal totalAvailable = entitlement.add(accrued).add(carried);
        BigDecimal unused = totalAvailable.subtract(consumed);
        if (unused.compareTo(BigDecimal.ZERO) < 0) {
            unused = BigDecimal.ZERO;
        }

        BigDecimal toCarry = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(policy.getCarryForwardEnabled())) {
            BigDecimal cap = policy.getCarryForwardCap();
            toCarry = cap != null ? unused.min(cap) : unused;
            if (policy.getCarryForwardExpiresAfterMonths() != null && policy.getCarryForwardExpiresAfterMonths() > 0) {
                allocation.setCarryForwardExpiresOn(asOf.plusMonths(policy.getCarryForwardExpiresAfterMonths()));
            } else {
                allocation.setCarryForwardExpiresOn(null);
            }
        } else {
            allocation.setCarryForwardExpiresOn(null);
        }

        // Mid-year resets (monthly, quarterly, half-yearly) within the same allocation:
        BigDecimal dropped = unused.subtract(toCarry);
        if (dropped.compareTo(BigDecimal.ZERO) > 0) {
            if (accrued.compareTo(dropped) >= 0) {
                accrued = accrued.subtract(dropped);
            } else {
                dropped = dropped.subtract(accrued);
                accrued = BigDecimal.ZERO;
                if (entitlement.compareTo(dropped) >= 0) {
                    entitlement = entitlement.subtract(dropped);
                } else {
                    dropped = dropped.subtract(entitlement);
                    entitlement = BigDecimal.ZERO;
                    carried = carried.subtract(dropped).max(BigDecimal.ZERO);
                }
            }
        }
        allocation.setEntitlementDays(entitlement);
        allocation.setAccruedDays(accrued);
        allocation.setCarriedForwardDays(carried);
        allocation.setLastResetOn(asOf);
        leaveAllocationRepository.save(allocation);
        return true;
    }
}
