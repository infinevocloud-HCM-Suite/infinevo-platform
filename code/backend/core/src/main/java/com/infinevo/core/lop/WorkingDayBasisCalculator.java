package com.infinevo.core.lop;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.holiday.HolidayQueryService;
import com.infinevo.core.holiday.HolidayResponse;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculator answering what a day of pay is worth for an employee and period (W-18.1, 12-core-contracts.md §3).
 *
 * <p>Produces payable days, the divisor for that day, and the policy id stamped onto pay figures (W-18.2).
 * Refuses to guess: throws {@link NoLopPolicyException} when no policy is in force or required configuration is absent.
 */
@Service
public class WorkingDayBasisCalculator {

    private final LopPolicyService policyService;
    private final EmployeeRepository employeeRepository;
    private final HolidayQueryService holidayQueryService;

    @Autowired(required = false)
    private WorkingWeekSource workingWeekSource;

    public WorkingDayBasisCalculator(
            LopPolicyService policyService,
            @Autowired(required = false) EmployeeRepository employeeRepository,
            @Autowired(required = false) HolidayQueryService holidayQueryService) {
        this.policyService = Objects.requireNonNull(policyService, "policyService must not be null");
        this.employeeRepository = employeeRepository;
        this.holidayQueryService = holidayQueryService;
    }

    /**
     * Resolves the working-day basis for the caller's bound tenant context.
     */
    @Transactional(readOnly = true)
    public WorkingDayBasisResponse basisFor(YearMonth period, UUID employeeId) {
        return basisFor(TenantContext.require(), period, employeeId);
    }

    /**
     * Core calculator entry point (12-core-contracts.md §3, W-18.1 §4).
     *
     * @param tenantId the tenant ID
     * @param period the pay period year-month
     * @param employeeId the employee ID
     * @return the payable days, divisor, and active policy ID
     * @throws NoLopPolicyException when no policy is in force or required configuration is absent
     */
    @Transactional(readOnly = true)
    public WorkingDayBasisResponse basisFor(UUID tenantId, YearMonth period, UUID employeeId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(period, "period must not be null");

        UUID previousTenant = TenantContext.current().orElse(null);
        try {
            TenantContext.set(tenantId);
            LocalDate endOfPeriod = period.atEndOfMonth();
            LopPolicy policy = policyService
                    .findPolicyInForceEntity(tenantId, endOfPeriod)
                    .orElseThrow(() -> new NoLopPolicyException(
                            "No loss-of-pay policy in force for tenant " + tenantId + " in period " + period));

            LocalDate from = period.atDay(1);
            LocalDate to = period.atEndOfMonth();

            return switch (policy.getWorkingDayBasis()) {
                case FIXED_30 -> {
                    BigDecimal days = BigDecimal.valueOf(30).setScale(2, RoundingMode.HALF_UP);
                    yield new WorkingDayBasisResponse(days, days, policy.getId());
                }
                case ORG_DAYS -> computeOrgDays(tenantId, period, employeeId, policy, from, to);
                case ACTUAL_DAYS -> computeActualDays(tenantId, period, employeeId, policy, from, to);
            };
        } finally {
            if (previousTenant != null) {
                TenantContext.set(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private WorkingDayBasisResponse computeActualDays(
            UUID tenantId, YearMonth period, UUID employeeId, LopPolicy policy, LocalDate from, LocalDate to) {
        if (policy.isWeekendsPayable() && policy.isHolidaysPayable()) {
            BigDecimal days = BigDecimal.valueOf(period.lengthOfMonth()).setScale(2, RoundingMode.HALF_UP);
            return new WorkingDayBasisResponse(days, days, policy.getId());
        }

        Set<DayOfWeek> workingDays = null;
        if (!policy.isWeekendsPayable()) {
            if (workingWeekSource == null) {
                throw new NoLopPolicyException(
                        "WorkingWeekSource bean is required to evaluate ACTUAL_DAYS when weekends are not payable");
            }
            workingDays = workingWeekSource.weekdaysFor(tenantId, employeeId);
            if (workingDays == null) {
                throw new NoLopPolicyException("WorkingWeekSource returned null weekday set for tenant " + tenantId);
            }
        }

        Set<LocalDate> holidayDates = Set.of();
        if (!policy.isHolidaysPayable()) {
            holidayDates = resolveHolidayDates(tenantId, employeeId, from, to);
        }

        int payableDaysCount = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (!policy.isWeekendsPayable() && !workingDays.contains(d.getDayOfWeek())) {
                continue;
            }
            if (!policy.isHolidaysPayable() && holidayDates.contains(d)) {
                continue;
            }
            payableDaysCount++;
        }

        BigDecimal days = BigDecimal.valueOf(payableDaysCount).setScale(2, RoundingMode.HALF_UP);
        return new WorkingDayBasisResponse(days, days, policy.getId());
    }

    private WorkingDayBasisResponse computeOrgDays(
            UUID tenantId, YearMonth period, UUID employeeId, LopPolicy policy, LocalDate from, LocalDate to) {
        if (policy.getConfiguredDaysPerMonth() != null) {
            BigDecimal configured = policy.getConfiguredDaysPerMonth().setScale(2, RoundingMode.HALF_UP);
            return new WorkingDayBasisResponse(configured, configured, policy.getId());
        }

        if (workingWeekSource == null) {
            throw new NoLopPolicyException(
                    "WorkingWeekSource bean is required to evaluate ORG_DAYS without configured days per month");
        }
        Set<DayOfWeek> workingDays = workingWeekSource.weekdaysFor(tenantId, employeeId);
        if (workingDays == null) {
            throw new NoLopPolicyException("WorkingWeekSource returned null weekday set for tenant " + tenantId);
        }

        Set<LocalDate> holidayDates = Set.of();
        if (!policy.isHolidaysPayable()) {
            holidayDates = resolveHolidayDates(tenantId, employeeId, from, to);
        }

        int payableDaysCount = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (!workingDays.contains(d.getDayOfWeek())) {
                continue;
            }
            if (!policy.isHolidaysPayable() && holidayDates.contains(d)) {
                continue;
            }
            payableDaysCount++;
        }

        BigDecimal days = BigDecimal.valueOf(payableDaysCount).setScale(2, RoundingMode.HALF_UP);
        return new WorkingDayBasisResponse(days, days, policy.getId());
    }

    private Set<LocalDate> resolveHolidayDates(UUID tenantId, UUID employeeId, LocalDate from, LocalDate to) {
        if (holidayQueryService == null) {
            throw new NoLopPolicyException(
                    "HolidayQueryService bean is required to evaluate loss-of-pay when holidays are not payable");
        }

        UUID locationId = null;
        if (employeeId != null && employeeRepository != null) {
            Optional<Employee> emp = employeeRepository.findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId);
            if (emp.isPresent() && emp.get().getWorkLocation() != null) {
                locationId = emp.get().getWorkLocation().getId();
            }
        }

        boolean contextSet = false;
        try {
            if (TenantContext.current().isEmpty()) {
                TenantContext.set(tenantId);
                contextSet = true;
            }
            List<HolidayResponse> holidays = holidayQueryService.holidaysBetween(locationId, from, to);
            if (holidays == null) {
                throw new NoLopPolicyException("Holiday lookup returned null for tenant " + tenantId);
            }
            Set<LocalDate> dates = new HashSet<>();
            for (HolidayResponse h : holidays) {
                LocalDate start = h.from().isBefore(from) ? from : h.from();
                LocalDate end = h.to().isAfter(to) ? to : h.to();
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    dates.add(d);
                }
            }
            return dates;
        } catch (NoLopPolicyException e) {
            throw e;
        } catch (Exception e) {
            throw new NoLopPolicyException(
                    "Could not resolve holidays for tenant " + tenantId + ": " + e.getMessage(), e);
        } finally {
            if (contextSet) {
                TenantContext.clear();
            }
        }
    }

    public void setWorkingWeekSource(WorkingWeekSource workingWeekSource) {
        this.workingWeekSource = workingWeekSource;
    }
}
