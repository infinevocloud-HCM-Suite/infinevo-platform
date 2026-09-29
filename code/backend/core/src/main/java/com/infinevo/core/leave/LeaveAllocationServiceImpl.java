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
                .orElseThrow(() -> new IllegalArgumentException("Leave type not found: " + request.leaveTypeId()));

        // Lookup employee joining date for pro-rate factor
        LocalDate joinDate = null;
        if (request.employeeId() != null) {
            Employee emp = employeeRepository
                    .findByIdAndTenantIdAndDeletedFalse(request.employeeId(), tenantId)
                    .orElse(null);
            if (emp != null) {
                joinDate = emp.getDateOfJoining();
            }
        }

        BigDecimal factor =
                LeaveProRate.calculateFactor(request.yearStartDate(), request.yearEndDate(), joinDate, null);

        // Lookup policy effective on yearStartDate
        LeavePolicy policy = policyRepository
                .findFirstByTenantIdAndLeaveTypeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDesc(
                        tenantId, request.leaveTypeId(), request.yearStartDate())
                .orElseThrow(() ->
                        new IllegalStateException("No effective policy found for leave type " + request.leaveTypeId()));

        BigDecimal entitlementDays;
        if (request.openingDays() != null) {
            entitlementDays = request.openingDays();
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
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");
        Objects.requireNonNull(newAnnualDays, "newAnnualDays must not be null");

        LocalDate evalDate = asOf != null ? asOf : LocalDate.now(ZoneOffset.UTC);

        List<LeaveAllocation> currentAllocations =
                allocationRepository
                        .findByTenantIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, leaveTypeId, evalDate, evalDate);

        List<OverdrawnEmployee> overdrawn = new ArrayList<>();
        for (LeaveAllocation allocation : currentAllocations) {
            BigDecimal newEntitlement = LeaveProRate.calculateEntitlement(newAnnualDays, allocation.getProRateFactor());
            BigDecimal accrued = allocation.getAccruedDays() != null ? allocation.getAccruedDays() : BigDecimal.ZERO;
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

        List<LeaveAllocation> currentAllocations =
                allocationRepository
                        .findByTenantIdAndLeaveTypeIdAndYearStartDateLessThanEqualAndYearEndDateGreaterThanEqual(
                                tenantId, leaveTypeId, evalDate, evalDate);

        for (LeaveAllocation allocation : currentAllocations) {
            BigDecimal newEntitlement =
                    LeaveProRate.calculateEntitlement(newPolicy.getAnnualDays(), allocation.getProRateFactor());
            allocation.setEntitlementDays(newEntitlement);
            allocation.setPolicyId(newPolicy.getId());
            allocationRepository.save(allocation);
        }
        return currentAllocations.size();
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
