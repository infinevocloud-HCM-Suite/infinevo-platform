package com.infinevo.hrms.timesheet.reminder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Which week a timesheet reminder chases (W-43.2 §3): last week, Monday to Sunday, as legacy
 * ({@code NotificationSchedularServiceImpl.java:86}).
 *
 * <p>Counted from the sweep's {@code slotDate}, the tenant-local day it is sending for (W-43.1), never from the
 * server's clock, so a tenant in another zone is chased about its own last week.
 */
final class ReminderWeek {

    private ReminderWeek() {}

    /** The Monday of the week before the one that contains {@code slotDate}. */
    static LocalDate weekStart(LocalDate slotDate) {
        return slotDate.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
