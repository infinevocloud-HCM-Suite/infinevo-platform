package com.infinevo.payroll.taxcalc.exception;

import com.infinevo.payroll.taxcalc.TaxRegime;

/**
 * Thrown when an income tax computation is requested for a regime whose calculator is not registered
 * (W-33.1 spec § 2, § 4).
 *
 * <p>Maps to HTTP 409 Conflict with reason code {@code REGIME_NOT_AVAILABLE}.
 */
public class RegimeNotAvailableException extends RuntimeException {

    private final String regime;

    public RegimeNotAvailableException(String regime) {
        super("Tax calculator is not available for regime: " + regime);
        this.regime = regime != null ? regime : "UNKNOWN";
    }

    public RegimeNotAvailableException(TaxRegime regime) {
        this(regime != null ? regime.name() : "UNKNOWN");
    }

    public String regime() {
        return regime;
    }

    public String getRegime() {
        return regime;
    }
}
