package com.infinevo.core.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GstCalculatorTest {

    private final GstCalculator calculator = new GstCalculator(new BillingProperties("27", "997331", 15));

    @Test
    void intrastateGstin_splitsIntoNinePercentCgstAndSgst() {
        GstCalculator.GstResult result = calculator.calculate(new BigDecimal("1000.00"), "27aabcu9603r1zm", null);

        assertThat(result.normalizedGstin()).isEqualTo("27AABCU9603R1ZM");
        assertThat(result.customerStateCode()).isEqualTo("27");
        assertThat(result.cgstAmount()).isEqualByComparingTo("90.00");
        assertThat(result.sgstAmount()).isEqualByComparingTo("90.00");
        assertThat(result.igstAmount()).isEqualByComparingTo("0.00");
        assertThat(result.totalTax()).isEqualByComparingTo("180.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("1180.00");
    }

    @Test
    void interstateStateCode_computesEighteenPercentIgst() {
        GstCalculator.GstResult result = calculator.calculate(new BigDecimal("1000.00"), "29AABCU9603R1ZM", null);

        assertThat(result.customerStateCode()).isEqualTo("29");
        assertThat(result.cgstAmount()).isEqualByComparingTo("0.00");
        assertThat(result.sgstAmount()).isEqualByComparingTo("0.00");
        assertThat(result.igstAmount()).isEqualByComparingTo("180.00");
        assertThat(result.totalTax()).isEqualByComparingTo("180.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("1180.00");
    }

    @Test
    void missingCustomerState_defaultsToInterstateIgst() {
        GstCalculator.GstResult result = calculator.calculate(new BigDecimal("1975.00"), null, null);

        assertThat(result.cgstAmount()).isEqualByComparingTo("0.00");
        assertThat(result.sgstAmount()).isEqualByComparingTo("0.00");
        assertThat(result.igstAmount()).isEqualByComparingTo("355.50");
        assertThat(result.totalAmount()).isEqualByComparingTo("2330.50");
    }

    @Test
    void mismatchedStateAndGstin_rejected() {
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100.00"), "27AABCU9603R1ZM", "29"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidGstin_rejected() {
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100.00"), "INVALID", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
