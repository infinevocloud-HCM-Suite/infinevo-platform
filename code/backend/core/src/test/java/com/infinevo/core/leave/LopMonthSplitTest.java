package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying month boundary splitting for loss-of-pay days (W-16.4a, spec section 7).
 */
class LopMonthSplitTest {

    private LopDerivationService service;

    @BeforeEach
    void setUp() {
        service = new LopDerivationServiceImpl();
    }

    @Test
    @DisplayName(
            "a request 28 Jan – 3 Feb with 4 excess days lands 1 day on 2026-01 and 3 on 2026-02 (last days first)")
    void multiMonthSplitLastDaysFirst() {
        LocalDate from = LocalDate.of(2026, 1, 28);
        LocalDate to = LocalDate.of(2026, 2, 3);
        BigDecimal excess = new BigDecimal("4.00");

        Map<YearMonth, BigDecimal> split =
                service.splitExcessDays(from, to, false, ExceedBalanceMode.MARK_AS_LOP, excess);

        assertThat(split).hasSize(2);
        assertThat(split.get(YearMonth.of(2026, 1))).isEqualByComparingTo("1.00");
        assertThat(split.get(YearMonth.of(2026, 2))).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("a half-day excess lands 0.50 on the appropriate month")
    void halfDayExcessLandsHalfDay() {
        LocalDate date = LocalDate.of(2026, 3, 31);
        BigDecimal excess = new BigDecimal("0.50");

        Map<YearMonth, BigDecimal> split =
                service.splitExcessDays(date, date, true, ExceedBalanceMode.MARK_AS_LOP, excess);

        assertThat(split).hasSize(1);
        assertThat(split.get(YearMonth.of(2026, 3))).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("a multi-month span where excess is fully within the second month")
    void excessFullyInSecondMonth() {
        LocalDate from = LocalDate.of(2026, 1, 28);
        LocalDate to = LocalDate.of(2026, 2, 3);
        BigDecimal excess = new BigDecimal("2.00"); // 2 Feb and 3 Feb

        Map<YearMonth, BigDecimal> split =
                service.splitExcessDays(from, to, false, ExceedBalanceMode.MARK_AS_LOP, excess);

        assertThat(split).hasSize(1);
        assertThat(split.get(YearMonth.of(2026, 2))).isEqualByComparingTo("2.00");
    }
}
