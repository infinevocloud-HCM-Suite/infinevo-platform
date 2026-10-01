package com.infinevo.payroll.taxcalc.engine;

import com.infinevo.payroll.taxcalc.model.SlabLine;
import com.infinevo.payroll.taxcalc.model.SlabTaxResult;
import com.infinevo.payroll.taxcalc.model.TaxSlabDetail;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure progressive income tax bracket calculator (W-33.1 spec ? 3 step 4).
 *
 * <p>Consumes taxable income bracket by bracket according to {@code reference.tax_slab_detail_history}.
 * A {@code null} {@code to_amount} represents the open-ended top slab.
 */
public final class SlabTax {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private SlabTax() {}

    /**
     * Calculates tax across statutory slabs.
     *
     * @param taxableIncome the taxable income
     * @param slabs statutory slab bracket details
     * @return {@link SlabTaxResult} containing total tax and per-bracket breakdown lines
     */
    public static SlabTaxResult of(Money taxableIncome, List<TaxSlabDetail> slabs) {
        Objects.requireNonNull(taxableIncome, "taxableIncome must not be null");
        Objects.requireNonNull(slabs, "slabs must not be null");

        if (taxableIncome.isNegative() || taxableIncome.isZero() || slabs.isEmpty()) {
            return new SlabTaxResult(Money.ZERO, List.of());
        }

        List<TaxSlabDetail> sortedSlabs = slabs.stream()
                .sorted(Comparator.comparingInt(TaxSlabDetail::slabOrder))
                .toList();

        List<SlabLine> lines = new ArrayList<>();
        Money totalTax = Money.ZERO;

        for (TaxSlabDetail slab : sortedSlabs) {
            Money from = slab.fromAmount();
            Money to = slab.toAmount();
            BigDecimal rate = slab.taxRatePercent();

            if (taxableIncome.compareTo(from) <= 0) {
                lines.add(new SlabLine(from, to, rate, Money.ZERO, Money.ZERO));
                continue;
            }

            Money upper = (to == null || taxableIncome.compareTo(to) < 0) ? taxableIncome : to;
            Money taxableInBracket = upper.subtract(from);

            Money taxForBracket;
            if (rate.compareTo(BigDecimal.ZERO) == 0 || taxableInBracket.isZero()) {
                taxForBracket = Money.ZERO;
            } else {
                BigDecimal factor = rate.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);
                taxForBracket = taxableInBracket.multiply(factor);
            }

            lines.add(new SlabLine(from, to, rate, taxableInBracket, taxForBracket));
            totalTax = totalTax.add(taxForBracket);
        }

        return new SlabTaxResult(totalTax, List.copyOf(lines));
    }
}
