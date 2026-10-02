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
 * Unit tests for {@link LopDerivationService} (W-16.4a, spec section 7).
 */
class LopDerivationServiceTest {

    private LopDerivationService service;

    @BeforeEach
    void setUp() {
        service = new LopDerivationServiceImpl();
    }

    @Test
    @DisplayName("consumption within balance yields zero LOP")
    void withinBalanceYieldsZero() {
        LocalDate from = LocalDate.of(2026, 4, 10);
        LocalDate to = LocalDate.of(2026, 4, 12);
        BigDecimal requested = new BigDecimal("3.00");
        BigDecimal balance = new BigDecimal("10.00");

        Map<YearMonth, BigDecimal> lop =
                service.deriveLop(from, to, false, ExceedBalanceMode.MARK_AS_LOP, requested, balance);

        assertThat(lop).isEmpty();
    }

    @Test
    @DisplayName("excess yields the difference under markAsLOP")
    void excessYieldsDifference() {
        LocalDate from = LocalDate.of(2026, 4, 10);
        LocalDate to = LocalDate.of(2026, 4, 14); // 5 days
        BigDecimal requested = new BigDecimal("5.00");
        BigDecimal balance = new BigDecimal("2.00"); // 3 excess days

        Map<YearMonth, BigDecimal> lop =
                service.deriveLop(from, to, false, ExceedBalanceMode.MARK_AS_LOP, requested, balance);

        assertThat(lop).hasSize(1);
        assertThat(lop.get(YearMonth.of(2026, 4))).isEqualByComparingTo("3.00");
    }

    @Test
    @DisplayName("half-days survive with 0.50 scale")
    void halfDaysSurvive() {
        LocalDate date = LocalDate.of(2026, 4, 15);
        BigDecimal requested = new BigDecimal("0.50");
        BigDecimal balance = BigDecimal.ZERO;

        Map<YearMonth, BigDecimal> lop =
                service.deriveLop(date, date, true, ExceedBalanceMode.MARK_AS_LOP, requested, balance);

        assertThat(lop).hasSize(1);
        assertThat(lop.get(YearMonth.of(2026, 4))).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("excess with fractional days preserves precision")
    void fractionalExcessPreservesPrecision() {
        LocalDate from = LocalDate.of(2026, 4, 10);
        LocalDate to = LocalDate.of(2026, 4, 12);
        BigDecimal requested = new BigDecimal("3.00");
        BigDecimal balance = new BigDecimal("1.50"); // excess = 1.50

        Map<YearMonth, BigDecimal> lop =
                service.deriveLop(from, to, false, ExceedBalanceMode.MARK_AS_LOP, requested, balance);

        assertThat(lop).hasSize(1);
        assertThat(lop.get(YearMonth.of(2026, 4))).isEqualByComparingTo("1.50");
    }
}
