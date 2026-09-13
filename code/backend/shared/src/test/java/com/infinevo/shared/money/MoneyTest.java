package com.infinevo.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Nested
    @DisplayName("scale and rounding")
    class ScaleAndRounding {

        @Test
        @DisplayName("carries calculation scale internally")
        void carriesCalculationScale() {
            assertThat(Money.of("10.5").raw().scale()).isEqualTo(Money.CALCULATION_SCALE);
        }

        @Test
        @DisplayName("toAmount() rounds to monetary scale, half up")
        void roundsHalfUpAtTheBoundary() {
            assertThat(Money.of("10.125").toAmount()).isEqualByComparingTo("10.13");
            assertThat(Money.of("10.124").toAmount()).isEqualByComparingTo("10.12");
        }

        @Test
        @DisplayName("rounds once at the boundary, not at every step")
        void roundsOnceNotPerStep() {
            // Three thirds of a rupee. Rounding each step would give 0.99;
            // rounding once at the end gives 1.00.
            Money third = Money.of("1.00").divide(new BigDecimal("3"));
            Money sum = third.add(third).add(third);

            assertThat(sum.toAmount()).isEqualByComparingTo("1.00");
        }
    }

    @Nested
    @DisplayName("arithmetic")
    class Arithmetic {

        @Test
        void addsAndSubtracts() {
            assertThat(Money.of("100.50").add(Money.of("49.50")).toAmount())
                    .isEqualByComparingTo("150.00");
            assertThat(Money.of("100.50").subtract(Money.of("0.50")).toAmount())
                    .isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("multiplies by a rate - the proration case")
        void multipliesByRate() {
            // 30000 a month, 12 of 30 days worked
            Money prorated = Money.of("30000").multiply(new BigDecimal("12"))
                    .divide(new BigDecimal("30"));

            assertThat(prorated.toAmount()).isEqualByComparingTo("12000.00");
        }

        @Test
        void negates() {
            assertThat(Money.of("10").negate().toAmount()).isEqualByComparingTo("-10.00");
        }

        @Test
        @DisplayName("division by zero throws rather than producing infinity")
        void divisionByZeroThrows() {
            assertThatThrownBy(() -> Money.of("10").divide(BigDecimal.ZERO))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    @Nested
    @DisplayName("comparison")
    class Comparison {

        @Test
        @DisplayName("equals by value, not by scale - the BigDecimal trap")
        void equalsByValueNotScale() {
            // new BigDecimal("0.00").equals(BigDecimal.ZERO) is false. Money must not be.
            assertThat(Money.of("0.00")).isEqualTo(Money.ZERO);
            assertThat(Money.of("10.0")).isEqualTo(Money.of("10.00"));
        }

        @Test
        @DisplayName("equal values hash equally")
        void equalValuesHashEqually() {
            assertThat(Money.of("10.0")).hasSameHashCodeAs(Money.of("10.00"));
        }

        @Test
        void ordersByValue() {
            assertThat(Money.of("10.01")).isGreaterThan(Money.of("10.00"));
            assertThat(Money.of("-1")).isLessThan(Money.ZERO);
        }

        @Test
        void reportsSign() {
            assertThat(Money.ZERO.isZero()).isTrue();
            assertThat(Money.of("0.00").isZero()).isTrue();
            assertThat(Money.of("0.01").isPositive()).isTrue();
            assertThat(Money.of("-0.01").isNegative()).isTrue();
        }
    }

    @Nested
    @DisplayName("no floating point can enter")
    class NoFloatingPoint {

        @Test
        @DisplayName("the classic 0.1 + 0.2 case is exact")
        void classicFloatingPointCaseIsExact() {
            // 0.1d + 0.2d == 0.30000000000000004
            assertThat(Money.of("0.1").add(Money.of("0.2")).toAmount())
                    .isEqualByComparingTo("0.30");
        }

        @Test
        @DisplayName("there is no factory taking a double or float")
        void noDoubleOrFloatFactory() {
            boolean hasFloatingPointFactory = java.util.Arrays.stream(Money.class.getMethods())
                    .filter(m -> m.getName().equals("of"))
                    .flatMap(m -> java.util.Arrays.stream(m.getParameterTypes()))
                    .anyMatch(t -> t == double.class || t == float.class
                            || t == Double.class || t == Float.class);

            assertThat(hasFloatingPointFactory)
                    .as("Money must not accept a floating-point value - CONVENTIONS.md section 2")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("formatting")
    class Formatting {

        // Every test here lives in a nested class. Surefire reports an outer class's own
        // @Test methods under the first nested class instead of the outer one, which makes
        // the per-class counts read wrongly. Keeping the grouping uniform avoids that.
        @Test
        @DisplayName("toString is plain at monetary scale, never scientific notation")
        void toStringIsPlain() {
            assertThat(Money.of("1250.5")).hasToString("1250.50");
            assertThat(Money.of("100000000")).hasToString("100000000.00");
        }
    }
}
