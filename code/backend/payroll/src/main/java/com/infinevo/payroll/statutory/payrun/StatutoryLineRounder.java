package com.infinevo.payroll.statutory.payrun;

import com.infinevo.shared.money.Money;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Statutory whole-rupee rounding rules (W-31.4 §3):
 *
 * <ul>
 *   <li>{@code EPF_EMPLOYEE}, {@code EPS_EMPLOYER}, {@code EDLI}, {@code EPF_ADMIN}: nearest rupee, {@link RoundingMode#HALF_UP}</li>
 *   <li>{@code EPF_EMPLOYER}: {@code roundPf(employer total) - roundPf(EPS)}, so the two sum to the employer's 12%</li>
 *   <li>{@code ESI_EMPLOYEE}, {@code ESI_EMPLOYER}: up to the next rupee, {@link RoundingMode#CEILING}</li>
 *   <li>{@code PROFESSIONAL_TAX}: the slab amount, already a whole rupee</li>
 * </ul>
 *
 * <p>All amounts returned are {@link Money} with calculation scale (4) holding an integer value.
 */
public final class StatutoryLineRounder {

    private StatutoryLineRounder() {}

    /** Rounds PF amounts to the nearest whole rupee (HALF_UP). */
    public static Money roundPf(Money amount) {
        if (amount == null) {
            return Money.ZERO;
        }
        return Money.of(amount.raw().setScale(0, RoundingMode.HALF_UP));
    }

    /** Rounds ESI amounts up to the next whole rupee (CEILING). */
    public static Money roundEsi(Money amount) {
        if (amount == null) {
            return Money.ZERO;
        }
        return Money.of(amount.raw().setScale(0, RoundingMode.CEILING));
    }

    /** Passes through professional tax amount as is. */
    public static Money roundPt(Money amount) {
        if (amount == null) {
            return Money.ZERO;
        }
        return amount;
    }

    /**
     * Derives {@code EPF_EMPLOYER} as {@code roundPf(employerTotal) - roundPf(eps)} so the two add
     * up to the employer's 12%.
     */
    public static Money roundEmployerPf(Money employerTotal, Money eps) {
        Money total = roundPf(employerTotal);
        Money roundedEps = roundPf(eps);
        Money diff = total.subtract(roundedEps);
        return diff.isNegative() ? Money.ZERO : diff;
    }

    /** Rounds a statutory line by its component code. */
    public static Money round(String componentCode, Money amount) {
        Objects.requireNonNull(componentCode, "componentCode must not be null");
        return switch (componentCode) {
            case "EPF_EMPLOYEE", "EPS_EMPLOYER", "EDLI", "EPF_ADMIN" -> roundPf(amount);
            case "ESI_EMPLOYEE", "ESI_EMPLOYER" -> roundEsi(amount);
            case "PROFESSIONAL_TAX" -> roundPt(amount);
            default -> roundPf(amount);
        };
    }
}
