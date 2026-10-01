package com.infinevo.payroll.taxcalc.model;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Breakdown of taxable income and computed tax for a single slab bracket (W-33.1).
 *
 * @param fromAmount the bracket floor
 * @param toAmount the bracket ceiling, or {@code null} if top band
 * @param taxRatePercent the bracket rate in percent
 * @param taxableAmount the portion of taxable income falling within this bracket
 * @param taxAmount the tax calculated on the taxable amount in this bracket
 */
public record SlabLine(
        Money fromAmount, Money toAmount, BigDecimal taxRatePercent, Money taxableAmount, Money taxAmount) {

    public SlabLine {
        Objects.requireNonNull(fromAmount, "fromAmount must not be null");
        Objects.requireNonNull(taxRatePercent, "taxRatePercent must not be null");
        Objects.requireNonNull(taxableAmount, "taxableAmount must not be null");
        Objects.requireNonNull(taxAmount, "taxAmount must not be null");
    }
}
