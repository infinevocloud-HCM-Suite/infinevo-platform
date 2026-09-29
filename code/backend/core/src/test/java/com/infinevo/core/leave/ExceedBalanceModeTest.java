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
 * Unit tests verifying behavior of different {@link ExceedBalanceMode}s (W-16.4a, spec section 7).
 */
class ExceedBalanceModeTest {

    private LopDerivationService service;

    @BeforeEach
    void setUp() {
        service = new LopDerivationServiceImpl();
    }

    @Test
    @DisplayName("the same excess under markAsLOP yields loss of pay; under noLimit and yearEndLimit it yields none")
    void compareExceedBalanceModes() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 5);
        BigDecimal requested = new BigDecimal("5.00");
        BigDecimal balance = new BigDecimal("2.00"); // excess = 3.00

        // 1. markAsLOP -> produces 3.00 days LOP
        Map<YearMonth, BigDecimal> lopMark =
                service.deriveLop(from, to, false, ExceedBalanceMode.MARK_AS_LOP, requested, balance);
        assertThat(lopMark).isNotEmpty();
        assertThat(lopMark.get(YearMonth.of(2026, 5))).isEqualByComparingTo("3.00");

        // 2. noLimit -> produces 0 LOP (balance goes negative)
        Map<YearMonth, BigDecimal> lopNoLimit =
                service.deriveLop(from, to, false, ExceedBalanceMode.NO_LIMIT, requested, balance);
        assertThat(lopNoLimit).isEmpty();

        // 3. yearEndLimit -> produces 0 LOP (balance goes negative up to limit)
        Map<YearMonth, BigDecimal> lopYearEnd =
                service.deriveLop(from, to, false, ExceedBalanceMode.YEAR_END_LIMIT, requested, balance);
        assertThat(lopYearEnd).isEmpty();
    }
}
