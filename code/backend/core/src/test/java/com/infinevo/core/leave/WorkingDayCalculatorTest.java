package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WorkingDayCalculator} (W-16.3, spec section 7).
 */
class WorkingDayCalculatorTest {

    private final WorkingDayCalculator calculator = new WorkingDayCalculator(null);

    @Test
    @DisplayName("weekends excluded by default")
    void weekendsExcludedByDefault() {
        // Mon 2026-10-05 to Sun 2026-10-11: 7 calendar days, 5 working days (Mon-Fri)
        LocalDate monday = LocalDate.of(2026, 10, 5);
        LocalDate sunday = LocalDate.of(2026, 10, 11);

        BigDecimal days = calculator.calculateWorkingDays(monday, sunday, false, false, false, List.of());
        assertThat(days).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    @DisplayName("weekends included when policy has includeWeekend true")
    void weekendsIncludedWhenConfigured() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        LocalDate sunday = LocalDate.of(2026, 10, 11);

        BigDecimal days = calculator.calculateWorkingDays(monday, sunday, false, true, false, List.of());
        assertThat(days).isEqualByComparingTo(new BigDecimal("7.00"));
    }

    @Test
    @DisplayName("holidays excluded when policy has includeHoliday false")
    void holidaysExcludedWhenConfigured() {
        // Mon 2026-10-05 to Wed 2026-10-07, with Tue 2026-10-06 as holiday
        LocalDate monday = LocalDate.of(2026, 10, 5);
        LocalDate wednesday = LocalDate.of(2026, 10, 7);
        LocalDate holiday = LocalDate.of(2026, 10, 6);

        BigDecimal days = calculator.calculateWorkingDays(monday, wednesday, false, false, false, List.of(holiday));
        assertThat(days).isEqualByComparingTo(new BigDecimal("2.00"));
    }

    @Test
    @DisplayName("holidays included when policy has includeHoliday true")
    void holidaysIncludedWhenConfigured() {
        LocalDate monday = LocalDate.of(2026, 10, 5);
        LocalDate wednesday = LocalDate.of(2026, 10, 7);
        LocalDate holiday = LocalDate.of(2026, 10, 6);

        BigDecimal days = calculator.calculateWorkingDays(monday, wednesday, false, false, true, List.of(holiday));
        assertThat(days).isEqualByComparingTo(new BigDecimal("3.00"));
    }

    @Test
    @DisplayName("a half-day is 0.50 on a regular working day")
    void halfDayIsHalfOnWorkingDay() {
        LocalDate wednesday = LocalDate.of(2026, 10, 7);

        BigDecimal days = calculator.calculateWorkingDays(wednesday, wednesday, true, false, false, List.of());
        assertThat(days).isEqualByComparingTo(new BigDecimal("0.50"));
    }

    @Test
    @DisplayName("a single-day request spanning a holiday is zero working days")
    void singleDaySpanningHolidayIsZero() {
        LocalDate holiday = LocalDate.of(2026, 10, 6);

        BigDecimal days = calculator.calculateWorkingDays(holiday, holiday, false, false, false, List.of(holiday));
        assertThat(days).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("a half-day on a holiday is zero working days")
    void halfDayOnHolidayIsZero() {
        LocalDate holiday = LocalDate.of(2026, 10, 6);

        BigDecimal days = calculator.calculateWorkingDays(holiday, holiday, true, false, false, List.of(holiday));
        assertThat(days).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("fromDate after toDate throws IllegalArgumentException")
    void fromDateAfterToDateThrows() {
        LocalDate start = LocalDate.of(2026, 10, 10);
        LocalDate end = LocalDate.of(2026, 10, 5);

        assertThatThrownBy(() -> calculator.calculateWorkingDays(start, end, false, false, false, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
