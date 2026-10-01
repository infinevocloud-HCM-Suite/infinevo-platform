package com.infinevo.payroll.taxcalc.model;

import com.infinevo.shared.money.Money;
import java.util.List;
import java.util.Objects;

/**
 * Result of computing progressive slab tax, including per-bracket lines (W-33.1).
 *
 * @param totalTax total tax before rebate or surcharge
 * @param lines individual slab lines consumed in the computation
 */
public record SlabTaxResult(Money totalTax, List<SlabLine> lines) {

    public SlabTaxResult {
        Objects.requireNonNull(totalTax, "totalTax must not be null");
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
