package com.infinevo.payroll.payrun;

import com.infinevo.shared.money.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * One line a {@link PayLineContributor} produces (W-29.2 §4): what it is, who wrote it, which
 * component it came from as that component was named on the day, and a non-negative amount at
 * calculation scale. {@code sort_order} is assigned by the computation, in contributor order.
 */
public record PayLine(
        LineKind kind,
        LineSource source,
        UUID componentId,
        String componentCode,
        String componentName,
        Money amount,
        boolean taxable) {

    public PayLine {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(componentCode, "componentCode must not be null");
        Objects.requireNonNull(componentName, "componentName must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("A pay line amount is never negative; the kind carries the sign");
        }
    }
}
