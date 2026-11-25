package com.infinevo.payroll.gratuity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GratuityCalculatorTest {

    @Test
    @DisplayName("Tenure < 5 years returns 0 for resignation")
    void tenureBelowFiveYearsReturnsZeroForResignation() {
        GratuityCalculator.Result result = GratuityCalculator.calculate(
                LocalDate.of(2022, 1, 1),
                LocalDate.of(2026, 6, 1),
                GratuityReason.RESIGNATION,
                new BigDecimal("50000.00"),
                new BigDecimal("10000.00"));

        assertThat(result.eligible()).isFalse();
        assertThat(result.exemptFrom5YrRule()).isFalse();
        assertThat(result.calculatedAmount()).isEqualByComparingTo("0.00");
        assertThat(result.grossGratuityPayable()).isEqualByComparingTo("0.00");
        assertThat(result.taxExemptAmount()).isEqualByComparingTo("0.00");
        assertThat(result.taxableAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Death or disablement waives 5-year rule")
    void deathWaivesFiveYearRule() {
        GratuityCalculator.Result result = GratuityCalculator.calculate(
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2026, 8, 1), // 3y 7m -> 4 years
                GratuityReason.DEATH,
                new BigDecimal("52000.00"),
                BigDecimal.ZERO);

        assertThat(result.eligible()).isTrue();
        assertThat(result.exemptFrom5YrRule()).isTrue();
        assertThat(result.completedYears()).isEqualTo(4);
        // 15 * 52000 * 4 / 26 = 120000.00
        assertThat(result.calculatedAmount()).isEqualByComparingTo("120000.00");
        assertThat(result.grossGratuityPayable()).isEqualByComparingTo("120000.00");
        assertThat(result.taxExemptAmount()).isEqualByComparingTo("120000.00");
        assertThat(result.taxableAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Tenure 5y 7m rounds up to 6 years; 5y 6m rounds down to 5 years")
    void sixMonthRoundingRule() {
        GratuityCalculator.Result roundedUp = GratuityCalculator.calculate(
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2025, 8, 1), // 5y 7m
                GratuityReason.RESIGNATION,
                new BigDecimal("26000.00"),
                new BigDecimal("0.00"));
        assertThat(roundedUp.eligible()).isTrue();
        assertThat(roundedUp.completedYears()).isEqualTo(6);
        // 15 * 26000 * 6 / 26 = 90000.00
        assertThat(roundedUp.calculatedAmount()).isEqualByComparingTo("90000.00");

        GratuityCalculator.Result roundedDown = GratuityCalculator.calculate(
                LocalDate.of(2020, 1, 1),
                LocalDate.of(2025, 7, 1), // 5y 6m 0d
                GratuityReason.RESIGNATION,
                new BigDecimal("26000.00"),
                new BigDecimal("0.00"));
        assertThat(roundedDown.eligible()).isTrue();
        assertThat(roundedDown.completedYears()).isEqualTo(5);
        // 15 * 26000 * 5 / 26 = 75000.00
        assertThat(roundedDown.calculatedAmount()).isEqualByComparingTo("75000.00");
    }

    @Test
    @DisplayName("Caps gross payable at 20L statutory ceiling and computes Section 10(10) split")
    void capsAtStatutoryCeilingAndSplitsTax() {
        // 15 * 260000 * 20 / 26 = 30,00,000.00 -> capped at 20,00,000.00
        GratuityCalculator.Result capped = GratuityCalculator.calculate(
                LocalDate.of(2006, 1, 1),
                LocalDate.of(2026, 1, 1),
                GratuityReason.RETIREMENT,
                new BigDecimal("200000.00"),
                new BigDecimal("60000.00"));

        assertThat(capped.completedYears()).isEqualTo(20);
        assertThat(capped.calculatedAmount()).isEqualByComparingTo("3000000.00");
        assertThat(capped.grossGratuityPayable()).isEqualByComparingTo("2000000.00");
        assertThat(capped.taxExemptAmount()).isEqualByComparingTo("2000000.00");
        assertThat(capped.taxableAmount()).isEqualByComparingTo("0.00");

        // When actual gratuity paid is 25,00,000.00 -> 20,00,000.00 exempt, 5,00,000.00 taxable
        GratuityCalculator.Result excessPaid = GratuityCalculator.calculate(
                LocalDate.of(2006, 1, 1),
                LocalDate.of(2026, 1, 1),
                GratuityReason.RETIREMENT,
                new BigDecimal("200000.00"),
                new BigDecimal("60000.00"),
                new BigDecimal("2500000.00"));

        assertThat(excessPaid.grossGratuityPayable()).isEqualByComparingTo("2500000.00");
        assertThat(excessPaid.taxExemptAmount()).isEqualByComparingTo("2000000.00");
        assertThat(excessPaid.taxableAmount()).isEqualByComparingTo("500000.00");
    }

    @Test
    @DisplayName("Rejects exitDate earlier than joiningDate")
    void rejectsExitBeforeJoining() {
        assertThatThrownBy(() -> GratuityCalculator.calculate(
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2024, 12, 31),
                        GratuityReason.RESIGNATION,
                        new BigDecimal("30000.00"),
                        BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exitDate");
    }
}
