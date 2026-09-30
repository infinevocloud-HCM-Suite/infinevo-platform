package com.infinevo.core.leave;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
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
    private final EmployeeRepository employeeRepository;

    public LeaveAccrualServiceImpl(
            LeaveAllocationRepository leaveAllocationRepository,
            LeavePolicyRepository leavePolicyRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    EmployeeRepository employeeRepository) {
        this.leaveAllocationRepository =
                Objects.requireNonNull(leaveAllocationRepository, "leaveAllocationRepository must not be null");
        this.leavePolicyRepository =
                Objects.requireNonNull(leavePolicyRepository, "leavePolicyRepository must not be null");
        this.employeeRepository = employeeRepository;
    }

    public LeaveAccrualServiceImpl(
            LeaveAllocationRepository leaveAllocationRepository, LeavePolicyRepository leavePolicyRepository) {
        this(leaveAllocationRepository, leavePolicyRepository, null);
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
            LocalDate joinDate = null;
            LocalDate terminationDate = null;
            if (employeeRepository != null && allocation.getEmployeeId() != null) {
                Employee emp = employeeRepository
                        .findByIdAndTenantIdAndDeletedFalse(allocation.getEmployeeId(), tenantId)
                        .orElse(null);
                if (emp != null) {
                    joinDate = emp.getDateOfJoining();
                    terminationDate = emp.getTerminationDate();
                }
            }

            YearMonth targetMonth = YearMonth.from(asOf);
            if (terminationDate != null && YearMonth.from(terminationDate).isBefore(targetMonth)) {
                targetMonth = YearMonth.from(terminationDate);
            }

            YearMonth startMonth;
            if (lastAccrued == null) {
                startMonth = YearMonth.from(allocation.getYearStartDate());
                if (joinDate != null && YearMonth.from(joinDate).isAfter(startMonth)) {
                    startMonth = YearMonth.from(joinDate);
                }
            } else {
                startMonth = YearMonth.from(lastAccrued).plusMonths(1);
            }

            if (startMonth.isAfter(targetMonth)) {
                return false; // Already accrued up to current month or not yet eligible
            }

            BigDecimal units = policy.getAccrualUnits();
            if (units == null || units.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }

            BigDecimal totalAccrual = BigDecimal.ZERO;
            YearMonth cur = startMonth;
            while (!cur.isAfter(targetMonth)) {
                BigDecimal monthUnits = units;
                if (Boolean.TRUE.equals(policy.getProRateEnabled())
                        && joinDate != null
                        && cur.equals(YearMonth.from(joinDate))) {
                    int totalDays = joinDate.lengthOfMonth();
                    int activeDays = totalDays - joinDate.getDayOfMonth() + 1;
                    if (activeDays < totalDays) {
                        BigDecimal factor = BigDecimal.valueOf(activeDays)
                                .divide(BigDecimal.valueOf(totalDays), 4, RoundingMode.HALF_UP);
                        monthUnits = units.multiply(factor).setScale(2, RoundingMode.HALF_UP);
                    }
                }
                totalAccrual = totalAccrual.add(monthUnits);
                cur = cur.plusMonths(1);
            }

            BigDecimal current = allocation.getAccruedDays() != null ? allocation.getAccruedDays() : BigDecimal.ZERO;
            allocation.setAccruedDays(current.add(totalAccrual));
            allocation.setLastAccruedOn(asOf);
            leaveAllocationRepository.save(allocation);
            return true;
        } else if (freq == AccrualFrequency.YEARLY) {
            if (lastAccrued != null
                    && !lastAccrued.isBefore(allocation.getYearStartDate())
                    && !lastAccrued.isAfter(allocation.getYearEndDate())) {
                return false; // Idempotent: already accrued for this allocation year
            }
            BigDecimal baseUnits = policy.getAccrualUnits() != null ? policy.getAccrualUnits() : policy.getAnnualDays();
            if (baseUnits == null || baseUnits.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
            BigDecimal factor = allocation.getProRateFactor() != null ? allocation.getProRateFactor() : BigDecimal.ONE;
            BigDecimal units = baseUnits.multiply(factor).setScale(2, RoundingMode.HALF_UP);

            BigDecimal current = allocation.getAccruedDays() != null ? allocation.getAccruedDays() : BigDecimal.ZERO;
            allocation.setAccruedDays(current.add(units));
            allocation.setLastAccruedOn(asOf);
            leaveAllocationRepository.save(allocation);
            return true;
        }

        return false;
    }
}
