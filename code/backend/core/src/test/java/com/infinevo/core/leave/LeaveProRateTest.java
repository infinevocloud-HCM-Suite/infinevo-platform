package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test for leave pro-rating calculations (W-16.2, spec section 7).
 * Covers:
 * - full year is a factor of exactly 1
 * - joining mid-year
 * - leaving mid-year
 * - joining and leaving mid-year
 * - joining after year end or leaving before year start
 */
class LeaveProRateTest {

    private static final LocalDate YEAR_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate YEAR_END = LocalDate.of(2026, 12, 31); // 365 days

    @Test
    @DisplayName("A full year produces a factor of exactly 1.0000")
    void fullYearFactorIsExactlyOne() {
        BigDecimal factor = LeaveProRate.calculateFactor(YEAR_START, YEAR_END, LocalDate.of(2025, 6, 1), null);
        assertThat(factor).isEqualByComparingTo(BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP));

        BigDecimal entitlement = LeaveProRate.calculateEntitlement(BigDecimal.valueOf(20), factor);
        assertThat(entitlement).isEqualByComparingTo(BigDecimal.valueOf(20).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Joining mid-year prorates factor based on active days")
    void joiningMidYearProrates() {
        // Joined July 1 (July 1 to Dec 31 = 184 days out of 365)
        LocalDate joinDate = LocalDate.of(2026, 7, 1);
        BigDecimal factor = LeaveProRate.calculateFactor(YEAR_START, YEAR_END, joinDate, null);

        // 184 / 365 = 0.504109... -> 0.5041
        BigDecimal expectedFactor = BigDecimal.valueOf(184).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);
        assertThat(factor).isEqualByComparingTo(expectedFactor);

        // Annual days 20 * 0.5041 = 10.082 -> 10.08
        BigDecimal entitlement = LeaveProRate.calculateEntitlement(BigDecimal.valueOf(20), factor);
        assertThat(entitlement).isEqualByComparingTo(BigDecimal.valueOf(10.08));
    }

    @Test
    @DisplayName("Leaving mid-year prorates factor based on active days")
    void leavingMidYearProrates() {
        // Left June 30 (Jan 1 to June 30 = 181 days out of 365)
        LocalDate leaveDate = LocalDate.of(2026, 6, 30);
        BigDecimal factor = LeaveProRate.calculateFactor(YEAR_START, YEAR_END, null, leaveDate);

        // 181 / 365 = 0.495890... -> 0.4959
        BigDecimal expectedFactor = BigDecimal.valueOf(181).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);
        assertThat(factor).isEqualByComparingTo(expectedFactor);

        BigDecimal entitlement = LeaveProRate.calculateEntitlement(BigDecimal.valueOf(20), factor);
        assertThat(entitlement).isEqualByComparingTo(BigDecimal.valueOf(9.92));
    }

    @Test
    @DisplayName("Joining and leaving both within the leave year")
    void joiningAndLeavingMidYear() {
        // March 1 to September 30 (31+30+31+30+31+31+30 = 214 days)
        LocalDate joinDate = LocalDate.of(2026, 3, 1);
        LocalDate leaveDate = LocalDate.of(2026, 9, 30);
        BigDecimal factor = LeaveProRate.calculateFactor(YEAR_START, YEAR_END, joinDate, leaveDate);

        BigDecimal expectedFactor = BigDecimal.valueOf(214).divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP);
        assertThat(factor).isEqualByComparingTo(expectedFactor);
    }

    @Test
    @DisplayName("Joining after year end returns factor 0.0000")
    void joiningAfterYearEndReturnsZero() {
        LocalDate joinDate = LocalDate.of(2027, 1, 15);
        BigDecimal factor = LeaveProRate.calculateFactor(YEAR_START, YEAR_END, joinDate, null);
        assertThat(factor).isEqualByComparingTo(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));

        BigDecimal entitlement = LeaveProRate.calculateEntitlement(BigDecimal.valueOf(20), factor);
        assertThat(entitlement).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("Invalid year parameters are rejected")
    void invalidYearParametersRejected() {
        assertThatThrownBy(() -> LeaveProRate.calculateFactor(YEAR_END, YEAR_START, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yearStart must not be after yearEnd");
    }
}
