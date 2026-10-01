package com.infinevo.payroll.taxcalc;

/**
 * Income tax regimes recognised by the calculation engine (W-33.1).
 */
public enum TaxRegime {
    NEW,
    OLD;

    public static TaxRegime from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tax regime must not be null or blank");
        }
        return TaxRegime.valueOf(value.trim().toUpperCase());
    }
}
