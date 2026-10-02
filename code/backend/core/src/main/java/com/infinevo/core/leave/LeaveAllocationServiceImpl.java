package com.infinevo.core.leave;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production implementation of {@link LeaveAllocationService} (W-16.2).
 */
@Service
@Transactional
public class LeaveAllocationServiceImpl implements LeaveAllocationService {

    private final LeaveAllocationRepository allocationRepository;
    private final LeavePolicyRepository policyRepository;
    private final LeaveTypeRepository typeRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveConsumptionRepository leaveConsumptionRepository;

    public LeaveAllocationServiceImpl(
            LeaveAllocationRepository allocationRepository,
            LeavePolicyRepository policyRepository,
            LeaveTypeRepository typeRepository,
            EmployeeRepository employeeRepository) {
        this(allocationRepository, policyRepository, typeRepository, employeeRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LeaveAllocationServiceImpl(
            LeaveAllocationRepository allocationRepository,
            LeavePolicyRepository policyRepository,
            LeaveTypeRepository typeRepository,
            EmployeeRepository employeeRepository,
            LeaveConsumptionRepository leaveConsumptionRepository) {
        this.allocationRepository =
                Objects.requireNonNull(allocationRepository, "allocationRepository must not be null");
        this.policyRepository = Objects.requireNonNull(policyRepository, "policyRepository must not be null");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository must not be null");
        this.employeeRepository = Objects.requireNonNull(employeeRepository, "employeeRepository must not be null");
        this.leaveConsumptionRepository = leaveConsumptionRepository;
    }

    @Override
    public LeaveAllocationResponse createAllocation(UUID tenantId, LeaveAllocationRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        // Verify leave type exists
        typeRepository
                .findByTenantIdAndId(tenantId, request.leaveTypeId())
                .orElseThrow(
                        () -> new java.util.NoSuchElementException("Leave type not found: " + request.leaveTypeId()));

        if (allocationRepository.existsOverlappingAllocation(
                tenantId,
                request.employeeId(),
                request.leaveTypeId(),
                request.yearStartDate(),
                request.yearEndDate())) {
            throw new IllegalStateException(
                    "An allocation already exists for this employee, leave type, and overlapping year period");
        }

        // Lookup employee joining and termination dates for pro-rate factor
        LocalDate joinDate = null;
        LocalDate terminationDate = null;
        if (request.employeeId() != null) {
            Employee emp = employeeRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.employeeId(), tenantId)
                    .orElse(null);
            if (emp != null) {
                joinDate = emp.getDateOfJoining();
                terminationDate = emp.getTerminationDate();
            }
        }

        // Lookup policy effective on yearStartDate
        LeavePolicy policy = policyRepository
                .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                        tenantId, request.leaveTypeId(), request.yearStartDate())
                .orElseThrow(() ->
                        new IllegalStateException("No effective policy found for leave type " + request.leaveTypeId()));

        BigDecimal factor = BigDecimal.ONE;
        if (Boolean.TRUE.equals(policy.getProRateEnabled())) {
            factor = LeaveProRate.calculateFactor(
                    request.yearStartDate(), request.yearEndDate(), joinDate, terminationDate);
        }

        BigDecimal entitlementDays;
        if (request.openingDays() != null) {
            entitlementDays = request.openingDays();
        } else if (Boolean.TRUE.equals(policy.getAccrualEnabled())) {
            entitlementDays = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            entitlementDays = LeaveProRate.calculateEntitlement(policy.getAnnualDays(), factor);
        }

        LeaveAllocation allocation = new LeaveAllocation(
                tenantId,
                request.employeeId(),
                request.leaveTypeId(),
                request.leaveYear(),
                request.yearStartDate(),
                request.yearEndDate(),
                entitlementDays,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                null,
                factor,
                policy.getId());

        LeaveAllocation saved = allocationRepository.save(allocation);
        return LeaveAllocationResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverdrawnEmployee> previewMidYearPolicyImpact(
            UUID tenantId, UUID leaveTypeId, BigDecimal newAnnualDays, LocalDate asOf) {
        return previewMidYearPolicyImpact(tenantId, leaveTypeId, newAnnualDays, null, asOf);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverdrawnEmployee> previewMidYearPolicyImpact(
            UUID tenantId, UUID leaveTypeId, BigDecimal newAnnualDays, Boolean newAccrualEnabled, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");
        Objects.requireNonNull(newAnnualDays, "newAnnualDays must not be null");

        LocalDate evalDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<LeaveAllocation> currentAllocations =
                allocationRepository
                        .findByTenantIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, leaveTypeId, evalDate, evalDate);

        List<OverdrawnEmployee> overdrawn = new ArrayList<>();
        for (LeaveAllocation allocation : currentAllocations) {
            // Apply leaves a closed year alone, so the preview does not report on it either.
            if (allocation.getYearEndDate().isBefore(today)) {
                continue;
            }
            boolean oldAccrual = isAccrual(allocation.getPolicyId());
            boolean newAccrual = newAccrualEnabled != null ? newAccrualEnabled : oldAccrual;
            MidYearOutcome outcome = midYearOutcome(allocation, oldAccrual, newAccrual, newAnnualDays);
            BigDecimal newEntitlement = outcome.entitlementDays();
            BigDecimal accrued = outcome.accruedDays();
            BigDecimal carried =
                    allocation.getCarriedForwardDays() != null ? allocation.getCarriedForwardDays() : BigDecimal.ZERO;
            BigDecimal consumed = BigDecimal.ZERO;
            if (leaveConsumptionRepository != null && allocation.getId() != null) {
                BigDecimal sum = leaveConsumptionRepository.sumConsumedDaysByAllocation(tenantId, allocation.getId());
                if (sum != null) {
                    consumed = sum;
                }
            }

            BigDecimal newRemaining = newEntitlement.add(accrued).add(carried).subtract(consumed);
            if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
                overdrawn.add(new OverdrawnEmployee(
                        allocation.getEmployeeId(),
                        leaveTypeId,
                        allocation.getEntitlementDays(),
                        newEntitlement,
                        newRemaining.abs()));
            }
        }
        return overdrawn;
    }

    @Override
    public int applyMidYearPolicyChange(UUID tenantId, UUID leaveTypeId, LeavePolicy newPolicy, LocalDate asOf) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");
        Objects.requireNonNull(newPolicy, "newPolicy must not be null");

        LocalDate evalDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<LeaveAllocation> currentAllocations =
                allocationRepository
                        .findByTenantIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, leaveTypeId, evalDate, evalDate);

        boolean newAccrual = Boolean.TRUE.equals(newPolicy.getAccrualEnabled());
        for (LeaveAllocation allocation : currentAllocations) {
            // Closed years must never be rewritten (Spec § 6)
            if (allocation.getYearEndDate().isBefore(today)) {
                continue;
            }
            // Read the outgoing policy before the allocation is re-pointed at the new one.
            boolean oldAccrual = isAccrual(allocation.getPolicyId());
            MidYearOutcome outcome = midYearOutcome(allocation, oldAccrual, newAccrual, newPolicy.getAnnualDays());
            allocation.setPolicyId(newPolicy.getId());
            allocation.setEntitlementDays(outcome.entitlementDays());
            allocation.setAccruedDays(outcome.accruedDays());
            if (outcome.accrualClosedForYear()) {
                allocation.setLastAccruedOn(allocation.getYearEndDate());
            }
            allocationRepository.save(allocation);
        }
        return currentAllocations.size();
    }

    private boolean isAccrual(UUID policyId) {
        if (policyId == null) {
            return false;
        }
        LeavePolicy policy = policyRepository.findById(policyId).orElse(null);
        return policy != null && Boolean.TRUE.equals(policy.getAccrualEnabled());
    }

    /** What one allocation holds after a mid-year policy change. Preview and apply both read it. */
    private record MidYearOutcome(BigDecimal entitlementDays, BigDecimal accruedDays, boolean accrualClosedForYear) {}

    /**
     * The one rule for a mid-year policy change, so the preview reports exactly what apply does.
     *
     * <ul>
     *   <li>fixed to fixed: the entitlement is recalculated from the new annual days;
     *   <li>accrual to accrual: nothing held changes - the days accrue progressively, and rewriting
     *       the entitlement would count them twice and destroy an imported opening balance;
     *   <li>accrual to fixed: the new entitlement replaces the days accrued so far, which become
     *       part of it and are not added on top;
     *   <li>fixed to accrual: the entitlement already granted for the year stands and accrual
     *       starts with the next leave year, so the grant is not accrued a second time.
     * </ul>
     */
    private static MidYearOutcome midYearOutcome(
            LeaveAllocation allocation, boolean oldAccrual, boolean newAccrual, BigDecimal newAnnualDays) {
        BigDecimal entitlement =
                allocation.getEntitlementDays() != null ? allocation.getEntitlementDays() : BigDecimal.ZERO;
        BigDecimal accrued = allocation.getAccruedDays() != null ? allocation.getAccruedDays() : BigDecimal.ZERO;
        if (newAccrual) {
            return new MidYearOutcome(entitlement, accrued, !oldAccrual);
        }
        BigDecimal newEntitlement = LeaveProRate.calculateEntitlement(newAnnualDays, allocation.getProRateFactor());
        return new MidYearOutcome(
                newEntitlement, oldAccrual ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : accrued, false);
    }

    @Override
    public LeaveAllocationResponse createAllocation(LeaveAllocationRequest request) {
        return createAllocation(TenantContext.require(), request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverdrawnEmployee> previewMidYearPolicyImpact(
            UUID leaveTypeId, BigDecimal newAnnualDays, LocalDate asOf) {
        return previewMidYearPolicyImpact(TenantContext.require(), leaveTypeId, newAnnualDays, asOf);
    }

    @Override
    public int applyMidYearPolicyChange(UUID leaveTypeId, LeavePolicy newPolicy, LocalDate asOf) {
        return applyMidYearPolicyChange(TenantContext.require(), leaveTypeId, newPolicy, asOf);
    }
}
