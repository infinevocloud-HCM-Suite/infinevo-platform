package com.infinevo.payroll.taxcalc.model;

import com.infinevo.shared.money.Money;
import java.util.Objects;

/**
 * Breakdown of surcharge, marginal relief, and health & education cess (W-33.1).
 *
 * @param nominalSurcharge surcharge before marginal relief
 * @param marginalRelief relief granted so tax plus surcharge does not exceed income above threshold
 * @param surcharge net surcharge after marginal relief
 * @param cess statutory health & education cess
 */
public record SurchargeAndCessResult(Money nominalSurcharge, Money marginalRelief, Money surcharge, Money cess) {

    public SurchargeAndCessResult {
        Objects.requireNonNull(nominalSurcharge, "nominalSurcharge must not be null");
        Objects.requireNonNull(marginalRelief, "marginalRelief must not be null");
        Objects.requireNonNull(surcharge, "surcharge must not be null");
        Objects.requireNonNull(cess, "cess must not be null");
    }
}
