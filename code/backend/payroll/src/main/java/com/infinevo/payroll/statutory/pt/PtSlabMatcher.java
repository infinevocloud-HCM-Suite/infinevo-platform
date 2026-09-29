package com.infinevo.payroll.statutory.pt;

import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Pure function that resolves a gross salary and pay period to a professional tax amount (W-31.2, spec section 3).
 *
 * <p>Contains no database access or Spring context. Receives {@link Money} in and produces {@link Money} out.
 */
public final class PtSlabMatcher {

    private PtSlabMatcher() {}

    /**
     * Resolves applicable professional tax according to the provided statutory or override slabs.
     *
     * @param gross the employee's gross salary subject to professional tax
     * @param gender the employee's gender (e.g. "female", "male")
     * @param periodEnd the pay period end date (never today's clock date)
     * @param slabs the sorted list of applicable slabs
     * @return the resolved professional tax {@link Money}
     */
    public static Money match(Money gross, String gender, LocalDate periodEnd, List<PtSlabDto> slabs) {
        Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (gross == null || gross.isNegative() || slabs == null || slabs.isEmpty()) {
            return Money.ZERO;
        }

        int month = periodEnd.getMonthValue();
        BigDecimal grossAmount = gross.raw();

        for (PtSlabDto slab : slabs) {
            if (slab.deductionMonths() != null
                    && !slab.deductionMonths().isEmpty()
                    && !slab.deductionMonths().contains(month)) {
                continue;
            }

            boolean matchFrom = grossAmount.compareTo(slab.fromAmount()) >= 0;
            boolean matchTo = slab.toAmount() == null || grossAmount.compareTo(slab.toAmount()) <= 0;

            if (matchFrom && matchTo) {
                if ("female".equalsIgnoreCase(gender) && slab.isFemaleExempt()) {
                    return Money.ZERO;
                }
                return Money.of(slab.amount());
            }
        }

        return Money.ZERO;
    }
}
