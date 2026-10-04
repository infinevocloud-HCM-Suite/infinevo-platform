package com.infinevo.payroll.taxdeclaration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link FinancialYear} value type (W-32.1, spec §7).
 */
class FinancialYearTest {

    @Test
    @DisplayName("2025-2026 parses correctly into start (1 Apr) and end (31 Mar)")
    void parsesValidFinancialYear() {
        FinancialYear fy = FinancialYear.parse("2025-2026");

        assertThat(fy.startYear()).isEqualTo(2025);
        assertThat(fy.endYear()).isEqualTo(2026);
        assertThat(fy.label()).isEqualTo("2025-2026");
        assertThat(fy.start()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(fy.end()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025", "2025-2027", "2026-2025", "25-26", "2025/2026", "abcd-efgh", ""})
    @DisplayName("Refuses malformed or non-consecutive financial year strings")
    void refusesInvalidFinancialYear(String input) {
        assertThatThrownBy(() -> FinancialYear.parse(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Refuses null financial year")
    void refusesNullFinancialYear() {
        assertThatThrownBy(() -> FinancialYear.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of(2025-03-31) resolves to 2024-2025 (last day of previous FY)")
    void resolvesMarch31ToPreviousFinancialYear() {
        FinancialYear fy = FinancialYear.of(LocalDate.of(2025, 3, 31));
        assertThat(fy.label()).isEqualTo("2024-2025");
        assertThat(fy.start()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(fy.end()).isEqualTo(LocalDate.of(2025, 3, 31));
    }

    @Test
    @DisplayName("of(2025-04-01) resolves to 2025-2026 (first day of new FY)")
    void resolvesApril1ToCurrentFinancialYear() {
        FinancialYear fy = FinancialYear.of(LocalDate.of(2025, 4, 1));
        assertThat(fy.label()).isEqualTo("2025-2026");
        assertThat(fy.start()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(fy.end()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("of(LocalDate) across various months inside 2025-2026")
    void resolvesMonthsWithinFinancialYear() {
        assertThat(FinancialYear.of(LocalDate.of(2025, 12, 25)).label()).isEqualTo("2025-2026");
        assertThat(FinancialYear.of(LocalDate.of(2026, 1, 1)).label()).isEqualTo("2025-2026");
        assertThat(FinancialYear.of(LocalDate.of(2026, 2, 28)).label()).isEqualTo("2025-2026");
    }

    @Test
    @DisplayName("Equality and hashCode behave as value objects")
    void equalityAndHashCode() {
        FinancialYear fy1 = FinancialYear.parse("2025-2026");
        FinancialYear fy2 = FinancialYear.of(2025, 2026);
        FinancialYear fy3 = FinancialYear.parse("2024-2025");

        assertThat(fy1).isEqualTo(fy2);
        assertThat(fy1.hashCode()).isEqualTo(fy2.hashCode());
        assertThat(fy1).isNotEqualTo(fy3);
        assertThat(fy1.toString()).isEqualTo("2025-2026");
    }

    @Test
    @DisplayName("allFrom resolves sequence of FY labels from past date to current date")
    void allFromMultiYearSequence() {
        LocalDate effectiveFrom = LocalDate.of(2023, 6, 1);
        LocalDate today = LocalDate.of(2025, 10, 15);

        java.util.List<String> fys = FinancialYear.allFrom(effectiveFrom, today);
        assertThat(fys).containsExactly("2023-2024", "2024-2025", "2025-2026");
    }

    @Test
    @DisplayName("allFrom resolves single FY when date is within current FY")
    void allFromSameYear() {
        LocalDate effectiveFrom = LocalDate.of(2025, 5, 1);
        LocalDate today = LocalDate.of(2025, 11, 1);

        java.util.List<String> fys = FinancialYear.allFrom(effectiveFrom, today);
        assertThat(fys).containsExactly("2025-2026");
    }

    @Test
    @DisplayName("allFrom resolves future FY if effectiveFrom is in future")
    void allFromFutureDate() {
        LocalDate effectiveFrom = LocalDate.of(2027, 4, 1);
        LocalDate today = LocalDate.of(2025, 11, 1);

        java.util.List<String> fys = FinancialYear.allFrom(effectiveFrom, today);
        assertThat(fys).containsExactly("2027-2028");
    }

    @Test
    @DisplayName("W-37: of(2026) is April 2026 to March 2027")
    void ofStartYear() {
        FinancialYear fy = FinancialYear.of(2026);
        assertThat(fy).isEqualTo(FinancialYear.of(2026, 2027));
        assertThat(fy.start()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(fy.end()).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    @DisplayName("W-37: 31 March 2026 is FY 2025; 1 April 2026 is FY 2026")
    void containingAppliesTheAprilRule() {
        assertThat(FinancialYear.containing(LocalDate.of(2026, 3, 31)).startYear())
                .isEqualTo(2025);
        assertThat(FinancialYear.containing(LocalDate.of(2026, 4, 1)).startYear())
                .isEqualTo(2026);
    }

    @Test
    @DisplayName("W-37: months() is April to March, in order")
    void monthsAprilToMarch() {
        assertThat(FinancialYear.of(2026).months())
                .hasSize(12)
                .startsWith(YearMonth.of(2026, 4), YearMonth.of(2026, 5))
                .endsWith(YearMonth.of(2027, 2), YearMonth.of(2027, 3))
                .isSorted();
    }
}
