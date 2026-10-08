package com.infinevo.payroll.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-73.6 §7: 10,000 over 3 instalments is 3,333.33 · 3,333.33 · 3,333.34 — the last takes the rounding. */
class ScheduledEarningSplitTest {

    @Test
    @DisplayName("10,000 ÷ 3 → 3,333.33 · 3,333.33 · 3,333.34")
    void tenThousandOverThree() {
        BigDecimal total = new BigDecimal("10000");
        assertThat(ScheduledEarningInstalments.amountOf(total, 3, 0)).isEqualByComparingTo("3333.33");
        assertThat(ScheduledEarningInstalments.amountOf(total, 3, 1)).isEqualByComparingTo("3333.33");
        assertThat(ScheduledEarningInstalments.amountOf(total, 3, 2)).isEqualByComparingTo("3333.34");
    }

    @Test
    @DisplayName("One instalment is the whole amount")
    void oneInstalment() {
        assertThat(ScheduledEarningInstalments.amountOf(new BigDecimal("30000.0000"), 1, 0))
                .isEqualByComparingTo("30000.00");
    }

    @Test
    @DisplayName("Every split of 1 to 12 instalments adds back to the total, to the paisa")
    void everySplitSumsToTotal() {
        BigDecimal[] totals = {
            new BigDecimal("10000"), new BigDecimal("0.01"), new BigDecimal("99999.9999"), new BigDecimal("7")
        };
        for (BigDecimal total : totals) {
            for (int n = 1; n <= 12; n++) {
                int instalments = n;
                BigDecimal sum = IntStream.range(0, instalments)
                        .mapToObj(i -> ScheduledEarningInstalments.amountOf(total, instalments, i))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(sum)
                        .as("%s over %d", total, instalments)
                        .isEqualByComparingTo(total.setScale(2, java.math.RoundingMode.HALF_UP));
            }
        }
    }

    @Test
    @DisplayName("F-3: 0.05 or 0.10 over 12 cannot be split; 0.18 over 12 can, every instalment at least 0.01")
    void tinyAmountsAreNotSplittable() {
        assertThat(ScheduledEarningInstalments.isSplittable(new BigDecimal("0.05"), 12))
                .isFalse();
        assertThat(ScheduledEarningInstalments.isSplittable(new BigDecimal("0.10"), 12))
                .isFalse();
        assertThat(ScheduledEarningInstalments.isSplittable(new BigDecimal("0.12"), 12))
                .isTrue();
        // Half-up would make this 0.02 x 11 and then -0.04; rounded down it is 0.01 x 11 and 0.07.
        BigDecimal total = new BigDecimal("0.18");
        for (int i = 0; i < 11; i++) {
            assertThat(ScheduledEarningInstalments.amountOf(total, 12, i)).isEqualByComparingTo("0.01");
        }
        assertThat(ScheduledEarningInstalments.amountOf(total, 12, 11)).isEqualByComparingTo("0.07");
    }

    @Test
    @DisplayName("F-3: whenever a split is allowed, every instalment is at least 0.01 (0.01 to 2.00, 1 to 12)")
    void everyAllowedSplitIsPositive() {
        for (int cents = 1; cents <= 200; cents++) {
            BigDecimal total = BigDecimal.valueOf(cents, 2);
            for (int n = 1; n <= 12; n++) {
                if (!ScheduledEarningInstalments.isSplittable(total, n)) {
                    continue;
                }
                for (int i = 0; i < n; i++) {
                    assertThat(ScheduledEarningInstalments.amountOf(total, n, i))
                            .as("%s over %d, instalment %d", total, n, i)
                            .isGreaterThanOrEqualTo(new BigDecimal("0.01"));
                }
            }
        }
    }

    @Test
    @DisplayName("The schedule id is read back from a ledger reference; other references give null")
    void scheduleIdOfReference() {
        UUID id = UUID.fromString("0d7c1a2e-3f4b-4c5d-8e6f-7a8b9c0d1e2f");
        assertThat(ScheduledEarningInstalments.scheduleIdOf(
                        ScheduledEarningInstalments.sourceRef(id, YearMonth.of(2026, 12))))
                .isEqualTo(id);
        assertThat(ScheduledEarningInstalments.scheduleIdOf("employee_deduction:" + id))
                .isNull();
        assertThat(ScheduledEarningInstalments.scheduleIdOf("scheduled_earning:not-a-uuid:2026-12"))
                .isNull();
    }

    @Test
    @DisplayName("An index outside the schedule, or fewer than one instalment, is refused")
    void boundsAreChecked() {
        assertThatThrownBy(() -> ScheduledEarningInstalments.amountOf(BigDecimal.TEN, 3, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScheduledEarningInstalments.amountOf(BigDecimal.TEN, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("The ledger reference names the schedule and the period, and fits source_ref's 64 characters")
    void sourceRefShape() {
        UUID id = UUID.fromString("0d7c1a2e-3f4b-4c5d-8e6f-7a8b9c0d1e2f");
        String ref = ScheduledEarningInstalments.sourceRef(id, YearMonth.of(2026, 11));
        assertThat(ref).isEqualTo("scheduled_earning:0d7c1a2e-3f4b-4c5d-8e6f-7a8b9c0d1e2f:2026-11");
        assertThat(ref.length()).isLessThanOrEqualTo(64);
    }
}
