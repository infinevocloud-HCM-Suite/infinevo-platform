package com.infinevo.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A monetary amount.
 *
 * <p>Exists to make the rules in {@code docs/CONVENTIONS.md} section 2 impossible to break
 * by accident, rather than merely documented:
 *
 * <ul>
 *   <li><strong>No floating point.</strong> There is no constructor taking a
 *       {@code double} or {@code float}, so the imprecision cannot enter</li>
 *   <li><strong>Round once, at the boundary.</strong> Arithmetic carries scale
 *       {@value #CALCULATION_SCALE}; {@link #toAmount()} rounds to
 *       {@value #MONETARY_SCALE} when the value is stored or displayed</li>
 *   <li><strong>Comparison by value.</strong> {@link #equals(Object)} uses
 *       {@code compareTo}, so {@code 0.00} equals {@code 0} - unlike raw
 *       {@link BigDecimal}, where it does not</li>
 * </ul>
 *
 * <p>Persist with {@code @Column(precision = 19, scale = 4)}.
 */
public final class Money implements Comparable<Money> {

    /** Scale money is stored and displayed at. */
    public static final int MONETARY_SCALE = 2;

    /** Scale carried through intermediate arithmetic, so rounding happens once. */
    public static final int CALCULATION_SCALE = 4;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(CALCULATION_SCALE));

    private final BigDecimal value;

    private Money(BigDecimal value) {
        this.value = value;
    }

    public static Money of(BigDecimal value) {
        Objects.requireNonNull(value, "value must not be null");
        return new Money(value.setScale(CALCULATION_SCALE, ROUNDING));
    }

    /** Prefer this for literals: {@code Money.of("1250.50")}. */
    public static Money of(String value) {
        Objects.requireNonNull(value, "value must not be null");
        return of(new BigDecimal(value));
    }

    public static Money of(long value) {
        return of(BigDecimal.valueOf(value));
    }

    public Money add(Money other) {
        return new Money(value.add(other.value));
    }

    public Money subtract(Money other) {
        return new Money(value.subtract(other.value));
    }

    /** Multiply by a rate or count - a percentage, a day count, a proration factor. */
    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor must not be null");
        return new Money(value.multiply(factor).setScale(CALCULATION_SCALE, ROUNDING));
    }

    public Money divide(BigDecimal divisor) {
        Objects.requireNonNull(divisor, "divisor must not be null");
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return new Money(value.divide(divisor, CALCULATION_SCALE, ROUNDING));
    }

    public Money negate() {
        return new Money(value.negate());
    }

    public boolean isZero() {
        return value.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return value.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return value.compareTo(BigDecimal.ZERO) < 0;
    }

    /** The value at calculation scale. Use inside a calculation, not for storage. */
    public BigDecimal raw() {
        return value;
    }

    /** The value rounded for storage or display. This is the boundary - round here, once. */
    public BigDecimal toAmount() {
        return value.setScale(MONETARY_SCALE, ROUNDING);
    }

    @Override
    public int compareTo(Money other) {
        return value.compareTo(other.value);
    }

    /** Value equality, not scale equality. {@code 0.00} equals {@code 0}. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Money other && value.compareTo(other.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    @Override
    public String toString() {
        return toAmount().toPlainString();
    }
}
