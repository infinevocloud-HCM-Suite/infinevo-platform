package com.infinevo.core.holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Connects the approval escalation seam ({@link com.infinevo.core.approval.HolidayQueryService},
 * W-15.3) to the real holiday calendar (W-17), so escalation's working-day count skips the
 * assignee's holidays.
 *
 * <p>Every date covered by a holiday range is returned, clipped to {@code [from, to]}. Restricted
 * (optional) holidays are left out: they are not a day off for everyone, so they do not pause an
 * approval clock.
 */
@Component
public class ApprovalHolidayQueryAdapter implements com.infinevo.core.approval.HolidayQueryService {

    private final HolidayQueryService holidayQueryService;

    public ApprovalHolidayQueryAdapter(HolidayQueryService holidayQueryService) {
        this.holidayQueryService = Objects.requireNonNull(holidayQueryService, "holidayQueryService must not be null");
    }

    @Override
    public List<LocalDate> holidaysBetween(UUID workLocationId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        TreeSet<LocalDate> dates = new TreeSet<>();
        for (HolidayResponse holiday : holidayQueryService.holidaysBetween(workLocationId, from, to)) {
            if (holiday.restricted()) {
                continue;
            }
            LocalDate start = holiday.from().isBefore(from) ? from : holiday.from();
            LocalDate end = holiday.to().isAfter(to) ? to : holiday.to();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                dates.add(d);
            }
        }
        return List.copyOf(dates);
    }
}
