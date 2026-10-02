package com.infinevo.payroll.tds;

import com.infinevo.payroll.taxcalc.TaxRegime;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Value carrier for the annual tax record figures (W-36.1 §4).
 *
 * <p>All money figures are held as {@link BigDecimal} at the stored scale; arithmetic on lines uses
 * {@code Money}.
 */
public record TdsFigures(
        TaxRegime regime,
        BigDecimal annualGross,
        BigDecimal annualTaxableIncome,
        BigDecimal annualTax,
        String effectiveFromPeriod,
        UUID declarationId,
        String note,
        TdsSource source) {

    public TdsFigures {
        Objects.requireNonNull(regime, "regime must not be null");
        Objects.requireNonNull(annualGross, "annualGross must not be null");
        Objects.requireNonNull(annualTaxableIncome, "annualTaxableIncome must not be null");
        Objects.requireNonNull(annualTax, "annualTax must not be null");
        if (source == null) {
            source = declarationId != null ? TdsSource.DECLARATION : TdsSource.OFFICER;
        }
    }

    public TdsFigures(
            TaxRegime regime,
            BigDecimal annualGross,
            BigDecimal annualTaxableIncome,
            BigDecimal annualTax,
            String effectiveFromPeriod,
            UUID declarationId,
            String note) {
        this(
                regime,
                annualGross,
                annualTaxableIncome,
                annualTax,
                effectiveFromPeriod,
                declarationId,
                note,
                declarationId != null ? TdsSource.DECLARATION : TdsSource.OFFICER);
    }
}
