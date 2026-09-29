package com.infinevo.worker.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.report.ReportSchedule;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReportScheduleEvaluator#isDue(ReportSchedule, ZonedDateTime, ZoneId)} (W-23.2 §7).
 *
 * <p>Covers daily, weekly, and monthly cadences, month-end clamping when a month has no 31st,
 * and tenant-local timezone evaluation.
 */
class ReportScheduleEvaluatorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata"); // UTC+05:30

    private ReportSchedule createSchedule(
            String cadence, Integer dayOfPeriod, LocalTime sendAtLocalTime, boolean isActive) {
        ReportSchedule s = new ReportSchedule(
                UUID.randomUUID(),
                UUID.randomUUID(),
                cadence,
                dayOfPeriod,
                sendAtLocalTime,
                "{}",
                "report@example.com",
                isActive,
                "test");
        return s;
    }

    @Nested
    @DisplayName("Daily cadence")
    class DailyCadenceTests {

        @Test
        @DisplayName("Due when send time has passed and has not run today")
        void dueWhenTimePassedAndNotRun() {
            ReportSchedule s = createSchedule("DAILY", null, LocalTime.of(9, 0), true);
            ZonedDateTime now = ZonedDateTime.of(2026, 9, 27, 9, 15, 0, 0, UTC);

            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isTrue();
        }

        @Test
        @DisplayName("Not due when local send time has not yet arrived")
        void notDueWhenTimeNotArrived() {
            ReportSchedule s = createSchedule("DAILY", null, LocalTime.of(9, 0), true);
            ZonedDateTime now = ZonedDateTime.of(2026, 9, 27, 8, 59, 0, 0, UTC);

            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isFalse();
        }

        @Test
        @DisplayName("Not due if already ran today")
        void notDueIfAlreadyRanToday() {
            ReportSchedule s = createSchedule("DAILY", null, LocalTime.of(9, 0), true);
            s.setLastRunAt(ZonedDateTime.of(2026, 9, 27, 9, 2, 0, 0, UTC).toInstant());

            ZonedDateTime now = ZonedDateTime.of(2026, 9, 27, 9, 30, 0, 0, UTC);

            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isFalse();
        }

        @Test
        @DisplayName("Due if ran yesterday")
        void dueIfRanYesterday() {
            ReportSchedule s = createSchedule("DAILY", null, LocalTime.of(9, 0), true);
            s.setLastRunAt(ZonedDateTime.of(2026, 9, 26, 9, 2, 0, 0, UTC).toInstant());

            ZonedDateTime now = ZonedDateTime.of(2026, 9, 27, 9, 15, 0, 0, UTC);

            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isTrue();
        }

        @Test
        @DisplayName("Inactive schedule is never due")
        void inactiveScheduleNeverDue() {
            ReportSchedule s = createSchedule("DAILY", null, LocalTime.of(9, 0), false);
            ZonedDateTime now = ZonedDateTime.of(2026, 9, 27, 10, 0, 0, 0, UTC);

            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isFalse();
        }
    }

    @Nested
    @DisplayName("Weekly cadence")
    class WeeklyCadenceTests {

        @Test
        @DisplayName("Due on matching day of week when time passed (Monday = 1)")
        void dueOnMatchingDayOfWeek() {
            // 2026-09-28 is a Monday (dayOfWeek = 1)
            ReportSchedule s = createSchedule("WEEKLY", 1, LocalTime.of(9, 0), true);
            ZonedDateTime now = ZonedDateTime.of(2026, 9, 28, 9, 30, 0, 0, UTC);

            assertThat(now.getDayOfWeek().getValue()).isEqualTo(1);
            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isTrue();
        }

        @Test
        @DisplayName("Not due on different day of week (e.g. Sunday = 7 when schedule is Monday = 1)")
        void notDueOnDifferentDayOfWeek() {
            // 2026-09-27 is Sunday (dayOfWeek = 7)
            ReportSchedule s = createSchedule("WEEKLY", 1, LocalTime.of(9, 0), true);
            ZonedDateTime now = ZonedDateTime.of(2026, 9, 27, 10, 0, 0, 0, UTC);

            assertThat(now.getDayOfWeek().getValue()).isEqualTo(7);
            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isFalse();
        }

        @Test
        @DisplayName("Not due if already ran today on the same day")
        void notDueIfAlreadyRanThisWeek() {
            ReportSchedule s = createSchedule("WEEKLY", 1, LocalTime.of(9, 0), true);
            s.setLastRunAt(ZonedDateTime.of(2026, 9, 28, 9, 5, 0, 0, UTC).toInstant());

            ZonedDateTime now = ZonedDateTime.of(2026, 9, 28, 10, 0, 0, 0, UTC);

            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isFalse();
        }
    }

    @Nested
    @DisplayName("Monthly cadence & Month-end clamping")
    class MonthlyCadenceTests {

        @Test
        @DisplayName("Due on matching day of month when month has that day")
        void dueOnMatchingDay() {
            ReportSchedule s = createSchedule("MONTHLY", 15, LocalTime.of(9, 0), true);
            ZonedDateTime now = ZonedDateTime.of(2026, 9, 15, 9, 30, 0, 0, UTC);

            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isTrue();
        }

        @Test
        @DisplayName("Not due on other day of month")
        void notDueOnOtherDay() {
            ReportSchedule s = createSchedule("MONTHLY", 15, LocalTime.of(9, 0), true);
            ZonedDateTime now = ZonedDateTime.of(2026, 9, 14, 10, 0, 0, 0, UTC);

            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isFalse();
        }

        @Test
        @DisplayName("Month-end clamping: Schedule for 31st runs on Feb 28 in a non-leap year")
        void monthEndClampingFebNonLeapYear() {
            // 2026 is non-leap, February has 28 days
            ReportSchedule s = createSchedule("MONTHLY", 31, LocalTime.of(9, 0), true);

            // On Feb 27: not due
            ZonedDateTime feb27 = ZonedDateTime.of(2026, 2, 27, 10, 0, 0, 0, UTC);
            assertThat(ReportScheduleEvaluator.isDue(s, feb27, UTC)).isFalse();

            // On Feb 28: clamped to 28, due!
            ZonedDateTime feb28 = ZonedDateTime.of(2026, 2, 28, 10, 0, 0, 0, UTC);
            assertThat(ReportScheduleEvaluator.isDue(s, feb28, UTC)).isTrue();
        }

        @Test
        @DisplayName("Month-end clamping: Schedule for 31st runs on Feb 29 in a leap year")
        void monthEndClampingFebLeapYear() {
            // 2024 is a leap year, February has 29 days
            ReportSchedule s = createSchedule("MONTHLY", 31, LocalTime.of(9, 0), true);

            // On Feb 28: not due (since month has 29 days)
            ZonedDateTime feb28 = ZonedDateTime.of(2024, 2, 28, 10, 0, 0, 0, UTC);
            assertThat(ReportScheduleEvaluator.isDue(s, feb28, UTC)).isFalse();

            // On Feb 29: clamped to 29, due!
            ZonedDateTime feb29 = ZonedDateTime.of(2024, 2, 29, 10, 0, 0, 0, UTC);
            assertThat(ReportScheduleEvaluator.isDue(s, feb29, UTC)).isTrue();
        }

        @Test
        @DisplayName("Month-end clamping: Schedule for 31st runs on April 30 (30-day month)")
        void monthEndClampingApril30() {
            ReportSchedule s = createSchedule("MONTHLY", 31, LocalTime.of(9, 0), true);

            // On April 29: not due
            ZonedDateTime apr29 = ZonedDateTime.of(2026, 4, 29, 10, 0, 0, 0, UTC);
            assertThat(ReportScheduleEvaluator.isDue(s, apr29, UTC)).isFalse();

            // On April 30: clamped to 30, due!
            ZonedDateTime apr30 = ZonedDateTime.of(2026, 4, 30, 10, 0, 0, 0, UTC);
            assertThat(ReportScheduleEvaluator.isDue(s, apr30, UTC)).isTrue();
        }

        @Test
        @DisplayName("Schedule for 31st runs on March 31 without clamping")
        void regularMonth31() {
            ReportSchedule s = createSchedule("MONTHLY", 31, LocalTime.of(9, 0), true);

            ZonedDateTime mar30 = ZonedDateTime.of(2026, 3, 30, 10, 0, 0, 0, UTC);
            assertThat(ReportScheduleEvaluator.isDue(s, mar30, UTC)).isFalse();

            ZonedDateTime mar31 = ZonedDateTime.of(2026, 3, 31, 10, 0, 0, 0, UTC);
            assertThat(ReportScheduleEvaluator.isDue(s, mar31, UTC)).isTrue();
        }

        @Test
        @DisplayName("Not due if already ran this month")
        void notDueIfAlreadyRanThisMonth() {
            ReportSchedule s = createSchedule("MONTHLY", 31, LocalTime.of(9, 0), true);
            s.setLastRunAt(ZonedDateTime.of(2026, 4, 30, 9, 5, 0, 0, UTC).toInstant());

            ZonedDateTime now = ZonedDateTime.of(2026, 4, 30, 11, 0, 0, 0, UTC);

            assertThat(ReportScheduleEvaluator.isDue(s, now, UTC)).isFalse();
        }
    }

    @Nested
    @DisplayName("Local time honoured across timezones")
    class TimezoneTests {

        @Test
        @DisplayName("A 09:00 report runs at 09:00 local time in India (03:30 UTC), not at 09:00 UTC")
        void localTimeHonouredInIndia() {
            ReportSchedule s = createSchedule("DAILY", null, LocalTime.of(9, 0), true);

            // At 03:25 UTC -> 08:55 IST (India). Not due yet.
            ZonedDateTime utc0325 = ZonedDateTime.of(2026, 9, 27, 3, 25, 0, 0, ZoneId.of("UTC"));
            ZonedDateTime ist0855 = utc0325.withZoneSameInstant(IST);
            assertThat(ReportScheduleEvaluator.isDue(s, ist0855, IST)).isFalse();

            // At 03:35 UTC -> 09:05 IST (India). Due!
            ZonedDateTime utc0335 = ZonedDateTime.of(2026, 9, 27, 3, 35, 0, 0, ZoneId.of("UTC"));
            ZonedDateTime ist0905 = utc0335.withZoneSameInstant(IST);
            assertThat(ReportScheduleEvaluator.isDue(s, ist0905, IST)).isTrue();

            // At the same instant (03:35 UTC), for a UTC tenant it is only 03:35 UTC -> not due.
            assertThat(ReportScheduleEvaluator.isDue(s, utc0335, UTC)).isFalse();
        }
    }

    @Nested
    @DisplayName("A send time after the last sweep of the day")
    class LateSlotTests {

        @Test
        @DisplayName("A 23:50 daily schedule is not due at 23:45, is due at 00:00, and not again at 00:15")
        void lateDailySlotIsPickedUpAfterMidnight() {
            ReportSchedule s = createSchedule("DAILY", null, LocalTime.of(23, 50), true);

            assertThat(ReportScheduleEvaluator.isDue(s, ZonedDateTime.of(2026, 9, 27, 23, 45, 0, 0, UTC), UTC))
                    .isFalse();
            ZonedDateTime midnight = ZonedDateTime.of(2026, 9, 28, 0, 0, 0, 0, UTC);
            assertThat(ReportScheduleEvaluator.isDue(s, midnight, UTC)).isTrue();

            s.setLastRunAt(midnight.plusSeconds(3).toInstant());
            assertThat(ReportScheduleEvaluator.isDue(s, ZonedDateTime.of(2026, 9, 28, 0, 15, 0, 0, UTC), UTC))
                    .isFalse();
            // The run at 00:00 was the 27th's slot; the 28th's own slot is still due the next night.
            assertThat(ReportScheduleEvaluator.isDue(s, ZonedDateTime.of(2026, 9, 29, 0, 0, 0, 0, UTC), UTC))
                    .isTrue();
        }

        @Test
        @DisplayName("A 23:50 month-end schedule runs from the first sweep of the next month, once")
        void lateMonthEndSlotIsPickedUpAfterMidnight() {
            ReportSchedule s = createSchedule("MONTHLY", 31, LocalTime.of(23, 50), true);

            ZonedDateTime may1 = ZonedDateTime.of(2026, 5, 1, 0, 0, 0, 0, IST);
            assertThat(ReportScheduleEvaluator.isDue(s, may1, IST)).isTrue();

            s.setLastRunAt(may1.plusSeconds(2).toInstant());
            assertThat(ReportScheduleEvaluator.isDue(s, may1.plusMinutes(15), IST))
                    .isFalse();
            assertThat(ReportScheduleEvaluator.isDue(s, ZonedDateTime.of(2026, 5, 2, 0, 0, 0, 0, IST), IST))
                    .isFalse();
        }

        @Test
        @DisplayName("A schedule saved before its send time does not fire at once as yesterday's slot")
        void aMissedSlotIsOnlyPickedUpWithinTheGrace() {
            ReportSchedule s = createSchedule("DAILY", null, LocalTime.of(9, 0), true);

            assertThat(ReportScheduleEvaluator.isDue(s, ZonedDateTime.of(2026, 9, 27, 8, 0, 0, 0, UTC), UTC))
                    .isFalse();
            assertThat(ReportScheduleEvaluator.isDue(s, ZonedDateTime.of(2026, 9, 27, 9, 59, 0, 0, UTC), UTC))
                    .isTrue();
        }
    }
}
