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
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculator answering what a day of pay is worth for an employee and period (W-18.1, 12-core-contracts.md §3).
 *
 * <p>Produces payable days, the divisor for that day, the policy id stamped onto pay figures (W-18.2),
 * and the policy's rounding rule.
 * Refuses to guess: throws {@link NoLopPolicyException} when no policy is in force, required configuration
 * is absent, the employee cannot be found or has no work location to pick a holiday calendar by, or the
 * period has no payable day.
 *
 * <p>{@link #daysOutsideEmployment} answers the same question for a joiner's or leaver's days outside the
 * employment window (W-18.2): in the policy's own days, so they are divided by a divisor of the same kind.
 */
@Service
public class WorkingDayBasisCalculator {

    /** A fixed basis's share of the month is kept this precise, so the money is rounded once, not twice. */
    private static final int SHARE_SCALE = 10;

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
     * @return the payable days, divisor, active policy ID and its rounding rule
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
                    yield response(days, policy);
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

    /**
     * The days of {@code period} before {@code dateOfJoining} or after {@code terminationDate}, counted the
     * way the policy in force counts the divisor (W-18.2), so that {@code divisor − outside} is what the
     * employee was employed for:
     *
     * <ul>
     *   <li>a counted basis — {@code ACTUAL_DAYS} with weekends or holidays unpaid, {@code ORG_DAYS} without
     *       configured days — counts the payable days in the gap by the same working week and holidays;
     *   <li>a fixed basis — {@code FIXED_30}, {@code ORG_DAYS} with configured days — takes the gap's share
     *       of the month: calendar days outside × divisor ÷ days in the month;
     *   <li>{@code ACTUAL_DAYS} with everything payable counts calendar days, as before.
     * </ul>
     *
     * <p>A July 2026 joiner on the 16th under Mon–Fri {@code ORG_DAYS} (divisor 23) is outside for the 11
     * working days of 1–15 July and is paid 12 of 23, not 8 of 23 as calendar days would give.
     *
     * <p>A fixed basis's share is rarely a whole number of days — 15 of 31 July days on {@code FIXED_30} is
     * 14.516… — so it is returned at scale 10, not 2: rounding the days and then the money rounds the
     * amount twice, which charged a 42,500 joiner 5.48 too much.
     *
     * @return the days outside, never more than the divisor; whole days at scale 2 under a counted basis,
     *     the unrounded share under a fixed one; zero when employed all period
     * @throws NoLopPolicyException as {@link #basisFor}
     */
    @Transactional(readOnly = true)
    public BigDecimal daysOutsideEmployment(
            UUID tenantId, YearMonth period, UUID employeeId, LocalDate dateOfJoining, LocalDate terminationDate) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(period, "period must not be null");
        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();
        LocalDate employedFrom = dateOfJoining != null && dateOfJoining.isAfter(from) ? dateOfJoining : from;
        LocalDate employedTo = terminationDate != null && terminationDate.isBefore(to) ? terminationDate : to;
        if (employedFrom.equals(from) && employedTo.equals(to)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        UUID previousTenant = TenantContext.current().orElse(null);
        try {
            TenantContext.set(tenantId);
            LopPolicy policy = policyService
                    .findPolicyInForceEntity(tenantId, to)
                    .orElseThrow(() -> new NoLopPolicyException(
                            "No loss-of-pay policy in force for tenant " + tenantId + " in period " + period));

            BigDecimal fixedDivisor = fixedDivisor(policy, period);
            if (fixedDivisor != null) {
                long outside = 0;
                for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                    if (d.isBefore(employedFrom) || d.isAfter(employedTo)) {
                        outside++;
                    }
                }
                BigDecimal share = BigDecimal.valueOf(outside)
                        .multiply(fixedDivisor)
                        .divide(BigDecimal.valueOf(period.lengthOfMonth()), SHARE_SCALE, RoundingMode.HALF_UP);
                return share.min(fixedDivisor);
            }

            boolean weekendsCount =
                    policy.getWorkingDayBasis() == WorkingDayBasis.ACTUAL_DAYS && policy.isWeekendsPayable();
            Set<DayOfWeek> workingDays = weekendsCount
                    ? null
                    : resolveWorkingDays(
                            tenantId,
                            employeeId,
                            "WorkingWeekSource bean is required to count a joiner's or leaver's days");
            Set<LocalDate> holidayDates =
                    policy.isHolidaysPayable() ? Set.of() : resolveHolidayDates(tenantId, employeeId, from, to);
            int outside = 0;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (!d.isBefore(employedFrom) && !d.isAfter(employedTo)) {
                    continue;
                }
                if (workingDays != null && !workingDays.contains(d.getDayOfWeek())) {
                    continue;
                }
                if (holidayDates.contains(d)) {
                    continue;
                }
                outside++;
            }
            return BigDecimal.valueOf(outside).setScale(2, RoundingMode.HALF_UP);
        } finally {
            if (previousTenant != null) {
                TenantContext.set(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * The divisor of a basis that does not count days — {@code FIXED_30}, {@code ORG_DAYS} with configured
     * days, {@code ACTUAL_DAYS} with everything payable (the month's calendar days) — or {@code null} for a
     * counted one.
     */
    private static BigDecimal fixedDivisor(LopPolicy policy, YearMonth period) {
        return switch (policy.getWorkingDayBasis()) {
            case FIXED_30 -> BigDecimal.valueOf(30).setScale(2, RoundingMode.HALF_UP);
            case ORG_DAYS ->
                policy.getConfiguredDaysPerMonth() == null
                        ? null
                        : policy.getConfiguredDaysPerMonth().setScale(2, RoundingMode.HALF_UP);
            case ACTUAL_DAYS ->
                policy.isWeekendsPayable() && policy.isHolidaysPayable()
                        ? BigDecimal.valueOf(period.lengthOfMonth()).setScale(2, RoundingMode.HALF_UP)
                        : null;
        };
    }

    private WorkingDayBasisResponse computeActualDays(
            UUID tenantId, YearMonth period, UUID employeeId, LopPolicy policy, LocalDate from, LocalDate to) {
        if (policy.isWeekendsPayable() && policy.isHolidaysPayable()) {
            BigDecimal days = BigDecimal.valueOf(period.lengthOfMonth()).setScale(2, RoundingMode.HALF_UP);
            return response(days, policy);
        }

        Set<DayOfWeek> workingDays = null;
        if (!policy.isWeekendsPayable()) {
            workingDays = resolveWorkingDays(
                    tenantId,
                    employeeId,
                    "WorkingWeekSource bean is required to evaluate ACTUAL_DAYS when weekends are not payable");
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

        return countedResponse(payableDaysCount, tenantId, period, policy);
    }

    private WorkingDayBasisResponse computeOrgDays(
            UUID tenantId, YearMonth period, UUID employeeId, LopPolicy policy, LocalDate from, LocalDate to) {
        if (policy.getConfiguredDaysPerMonth() != null) {
            BigDecimal configured = policy.getConfiguredDaysPerMonth().setScale(2, RoundingMode.HALF_UP);
            return response(configured, policy);
        }

        Set<DayOfWeek> workingDays = resolveWorkingDays(
                tenantId,
                employeeId,
                "WorkingWeekSource bean is required to evaluate ORG_DAYS without configured days per month");

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

        return countedResponse(payableDaysCount, tenantId, period, policy);
    }

    /**
     * A counted basis of zero days would make the divisor zero and a day's pay undefined. Refuse
     * rather than hand W-18.2 a division by zero.
     */
    private static WorkingDayBasisResponse countedResponse(
            int payableDaysCount, UUID tenantId, YearMonth period, LopPolicy policy) {
        if (payableDaysCount <= 0) {
            throw new NoLopPolicyException("No payable days in period " + period + " for tenant " + tenantId
                    + " under loss-of-pay policy " + policy.getId());
        }
        return response(BigDecimal.valueOf(payableDaysCount).setScale(2, RoundingMode.HALF_UP), policy);
    }

    private static WorkingDayBasisResponse response(BigDecimal days, LopPolicy policy) {
        return new WorkingDayBasisResponse(days, days, policy.getId(), policy.getLopRounding());
    }

    /**
     * Reads the working week through the port. Every failure — no bean, no pay schedule
     * ({@code NoPayScheduleException} is a {@link NoLopPolicyException}), or anything else the source
     * throws — reaches the caller as {@link NoLopPolicyException}, the same shape as a holiday failure.
     */
    private Set<DayOfWeek> resolveWorkingDays(UUID tenantId, UUID employeeId, String missingBeanMessage) {
        if (workingWeekSource == null) {
            throw new NoLopPolicyException(missingBeanMessage);
        }
        Set<DayOfWeek> workingDays;
        try {
            workingDays = workingWeekSource.weekdaysFor(tenantId, employeeId);
        } catch (NoLopPolicyException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new NoLopPolicyException(
                    "Could not resolve the working week for tenant " + tenantId + ": " + e.getMessage(), e);
        }
        if (workingDays == null) {
            throw new NoLopPolicyException("WorkingWeekSource returned null weekday set for tenant " + tenantId);
        }
        return workingDays;
    }

    private Set<LocalDate> resolveHolidayDates(UUID tenantId, UUID employeeId, LocalDate from, LocalDate to) {
        if (holidayQueryService == null) {
            throw new NoLopPolicyException(
                    "HolidayQueryService bean is required to evaluate loss-of-pay when holidays are not payable");
        }

        boolean contextSet = false;
        try {
            if (TenantContext.current().isEmpty()) {
                TenantContext.set(tenantId);
                contextSet = true;
            }
            UUID locationId = resolveLocationId(tenantId, employeeId);
            List<HolidayResponse> holidays = holidayQueryService.holidaysBetween(locationId, from, to);
            if (holidays == null) {
                throw new NoLopPolicyException("Holiday lookup returned null for tenant " + tenantId);
            }
            Set<LocalDate> dates = new HashSet<>();
            for (HolidayResponse h : holidays) {
                // A restricted (optional) holiday is taken by some employees, not all: it is not a
                // day off for the location, so it never reduces payable days or the divisor.
                if (h.restricted()) {
                    continue;
                }
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

    /**
     * The employee's work location, which picks the holiday calendar. No employee means the tenant's
     * default calendar. An employee that is unknown, deleted or in another tenant is refused, and so is
     * one with no work location (W-18.2 §7): giving either the default calendar would be a guess, and
     * the pay run fails that employee alone rather than pay them by the wrong holidays.
     */
    private UUID resolveLocationId(UUID tenantId, UUID employeeId) {
        if (employeeId == null) {
            return null;
        }
        if (employeeRepository == null) {
            throw new NoLopPolicyException(
                    "EmployeeRepository bean is required to resolve the holiday calendar of employee " + employeeId);
        }
        Employee employee = employeeRepository
                .findByIdAndTenantIdAndDeletedFalse(employeeId, tenantId)
                .orElseThrow(() -> new NoLopPolicyException("Employee " + employeeId + " not found in tenant "
                        + tenantId + "; cannot resolve its holiday calendar"));
        if (employee.getWorkLocation() == null) {
            throw new NoLopPolicyException("Employee " + employeeId + " has no work location; its holiday calendar"
                    + " cannot be chosen, so its loss of pay cannot be priced");
        }
        return employee.getWorkLocation().getId();
    }

    public void setWorkingWeekSource(WorkingWeekSource workingWeekSource) {
        this.workingWeekSource = workingWeekSource;
    }
}
