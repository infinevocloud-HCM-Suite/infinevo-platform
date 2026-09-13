package com.itsdev.payroll.dto.employeeitdeclaration.taxCalculator;

import java.math.BigDecimal;

/**
 * One slab row as exchanged with the UI.
 *
 * IMPORTANT — field names ("from", "to", "rate") and types match EXACTLY the
 * inner TaxSlab the tax engine deserializes from {@code slabJson}
 * (see NewTaxCalculationServiceImpl / OldTaxCalculationServiceImpl). The UI edits
 * these rows; the service re-serializes them into the same JSON shape so the
 * engine keeps working unchanged.
 *
 * {@code to == null} means the highest, open-ended slab ("and above").
 */
public class TaxSlabRowDTO {

    private BigDecimal from;
    private BigDecimal to;   // null => open-ended top slab
    private BigDecimal rate; // percentage, 0..100

    public TaxSlabRowDTO() {}

    public TaxSlabRowDTO(BigDecimal from, BigDecimal to, BigDecimal rate) {
        this.from = from;
        this.to = to;
        this.rate = rate;
    }

    public BigDecimal getFrom() { return from; }
    public void setFrom(BigDecimal from) { this.from = from; }

    public BigDecimal getTo() { return to; }
    public void setTo(BigDecimal to) { this.to = to; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
}
