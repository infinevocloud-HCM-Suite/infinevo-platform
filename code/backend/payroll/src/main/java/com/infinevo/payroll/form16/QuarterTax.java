package com.infinevo.payroll.form16;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Quarterly tax deduction summary on Form 16 (W-36.4).
 */
public record QuarterTax(String quarter, BigDecimal amountDeducted) {

    public QuarterTax {
        Objects.requireNonNull(quarter, "quarter must not be null");
        amountDeducted = amountDeducted != null
                ? amountDeducted.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
