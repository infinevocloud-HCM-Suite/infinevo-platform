package com.infinevo.hrms.timesheet.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-43.2 §7, {@code ReminderWeekTest}: whatever day of the week the sweep sends on, it chases last week. */
class ReminderWeekTest {

    private static final LocalDate LAST_MONDAY = LocalDate.of(2026, 9, 28);

    @Test
    @DisplayName("Monday, Wednesday and Sunday slots all give last week's Monday")
    void anyDayOfTheWeekGivesLastWeeksMonday() {
        assertThat(ReminderWeek.weekStart(LocalDate.of(2026, 10, 5)))
                .as("Monday")
                .isEqualTo(LAST_MONDAY);
        assertThat(ReminderWeek.weekStart(LocalDate.of(2026, 10, 7)))
                .as("Wednesday")
                .isEqualTo(LAST_MONDAY);
        assertThat(ReminderWeek.weekStart(LocalDate.of(2026, 10, 11)))
                .as("Sunday")
                .isEqualTo(LAST_MONDAY);
    }

    @Test
    @DisplayName("The next day, Monday, moves on to the next week")
    void theNextMondayMovesOn() {
        assertThat(ReminderWeek.weekStart(LocalDate.of(2026, 10, 12))).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    @DisplayName("1 January: the week before may start in the old year")
    void aSlotInEarlyJanuaryChasesTheOldYear() {
        // Friday 2027-01-01. The week before it ran Monday 2026-12-21 to Sunday 2026-12-27.
        assertThat(ReminderWeek.weekStart(LocalDate.of(2027, 1, 1))).isEqualTo(LocalDate.of(2026, 12, 21));
        // Monday 2026-01-05 chases the week that began Monday 2025-12-29.
        assertThat(ReminderWeek.weekStart(LocalDate.of(2026, 1, 5))).isEqualTo(LocalDate.of(2025, 12, 29));
    }

    @Test
    @DisplayName("The result is always a Monday")
    void alwaysAMonday() {
        for (LocalDate day = LocalDate.of(2026, 12, 20);
                day.isBefore(LocalDate.of(2027, 1, 20));
                day = day.plusDays(1)) {
            assertThat(ReminderWeek.weekStart(day).getDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY);
            assertThat(ReminderWeek.weekStart(day)).isBefore(day);
        }
    }
}
