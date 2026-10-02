package com.infinevo.payroll.taxcalc.model;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A statutory tax bracket from {@code reference.tax_slab_detail_history} (W-33.1).
 *
 * @param fromAmount the bracket floor, inclusive
 * @param toAmount the bracket ceiling, inclusive, or {@code null} for open-ended top bracket
 * @param taxRatePercent the statutory percentage (e.g. 5.00 for 5%)
 * @param slabOrder the ascending order index of the slab (1, 2, 3...)
 */
public record TaxSlabDetail(Money fromAmount, Money toAmount, BigDecimal taxRatePercent, int slabOrder) {

    public TaxSlabDetail {
        Objects.requireNonNull(fromAmount, "fromAmount must not be null");
        Objects.requireNonNull(taxRatePercent, "taxRatePercent must not be null");
        if (toAmount != null && toAmount.compareTo(fromAmount) < 0) {
            throw new IllegalArgumentException("toAmount cannot be less than fromAmount");
        }
    }
}
