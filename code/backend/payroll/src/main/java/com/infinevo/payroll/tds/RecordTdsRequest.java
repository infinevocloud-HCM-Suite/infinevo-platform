package com.infinevo.payroll.tds;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxcalc.TaxRegime;
import java.math.BigDecimal;

/**
 * Request payload for officer TDS PUT (W-36.1 §4).
 */
public record RecordTdsRequest(
        @JsonProperty("regime") TaxRegime regime,
        @JsonProperty("annual_gross") BigDecimal annualGross,
        @JsonProperty("annual_taxable_income") BigDecimal annualTaxableIncome,
        @JsonProperty("annual_tax") BigDecimal annualTax,
        @JsonProperty("effective_from_period") String effectiveFromPeriod,
        @JsonProperty("note") String note) {

    public TdsFigures toFigures() {
        return new TdsFigures(regime, annualGross, annualTaxableIncome, annualTax, effectiveFromPeriod, null, note);
    }
}
