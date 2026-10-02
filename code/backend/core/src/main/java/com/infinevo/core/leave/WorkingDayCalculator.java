package com.infinevo.core.leave;

import com.infinevo.core.approval.HolidayQueryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Calculates working days for a leave request based on policy weekend and holiday inclusion flags (W-16.3).
 * Excludes weekends and public/company holidays unless the policy explicitly includes them.
 */
@Component
public class WorkingDayCalculator {

    public static final BigDecimal HALF_DAY = new BigDecimal("0.50");
    public static final BigDecimal ZERO_DAYS = new BigDecimal("0.00");

    private final HolidayQueryService holidayQueryService;

    public WorkingDayCalculator(@Autowired(required = false) HolidayQueryService holidayQueryService) {
        this.holidayQueryService = holidayQueryService != null ? holidayQueryService : new HolidayQueryService() {};
    }

    /**
     * Calculates working days for a leave request using policy configuration.
     *
     * @param fromDate start date (inclusive)
     * @param toDate end date (inclusive)
     * @param isHalfDay true if half-day request
     * @param policy leave policy governing the request
     * @param workLocationId optional work location for holiday lookup
     * @return calculated working days with scale 2
     */
    public BigDecimal calculateWorkingDays(
            LocalDate fromDate, LocalDate toDate, boolean isHalfDay, LeavePolicy policy, UUID workLocationId) {
        boolean includeWeekend = policy != null && Boolean.TRUE.equals(policy.getIncludeWeekend());
        boolean includeHoliday = policy != null && Boolean.TRUE.equals(policy.getIncludeHoliday());
        return calculateWorkingDays(fromDate, toDate, isHalfDay, includeWeekend, includeHoliday, workLocationId);
    }

    /**
     * Calculates working days using explicit inclusion flags and location holiday query.
     */
    public BigDecimal calculateWorkingDays(
            LocalDate fromDate,
            LocalDate toDate,
            boolean isHalfDay,
            boolean includeWeekend,
            boolean includeHoliday,
            UUID workLocationId) {
        Objects.requireNonNull(fromDate, "fromDate must not be null");
        Objects.requireNonNull(toDate, "toDate must not be null");
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate cannot be after toDate: " + fromDate + " > " + toDate);
        }

        List<LocalDate> holidays = (!includeHoliday && holidayQueryService != null)
                ? holidayQueryService.holidaysBetween(workLocationId, fromDate, toDate)
                : Collections.emptyList();

        return calculateWorkingDays(fromDate, toDate, isHalfDay, includeWeekend, includeHoliday, holidays);
    }

    /**
     * Calculates working days using explicit holiday list.
     */
    public BigDecimal calculateWorkingDays(
            LocalDate fromDate,
            LocalDate toDate,
            boolean isHalfDay,
            boolean includeWeekend,
            boolean includeHoliday,
            List<LocalDate> holidays) {
        Objects.requireNonNull(fromDate, "fromDate must not be null");
        Objects.requireNonNull(toDate, "toDate must not be null");
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate cannot be after toDate: " + fromDate + " > " + toDate);
        }

        Set<LocalDate> holidaySet =
                (holidays != null && !includeHoliday) ? new HashSet<>(holidays) : Collections.emptySet();

        long workingDayCount = 0;
        LocalDate current = fromDate;
        while (!current.isAfter(toDate)) {
            boolean isWeekend =
                    current.getDayOfWeek() == DayOfWeek.SATURDAY || current.getDayOfWeek() == DayOfWeek.SUNDAY;
            boolean isHoliday = holidaySet.contains(current);

            boolean countThisDay = true;
            if (isWeekend && !includeWeekend) {
                countThisDay = false;
            }
            if (isHoliday && !includeHoliday) {
                countThisDay = false;
            }

            if (countThisDay) {
                workingDayCount++;
            }
            current = current.plusDays(1);
        }

        if (isHalfDay) {
            if (workingDayCount == 0) {
                return ZERO_DAYS;
            }
            return HALF_DAY;
        }

        return BigDecimal.valueOf(workingDayCount).setScale(2, RoundingMode.HALF_UP);
    }
}
