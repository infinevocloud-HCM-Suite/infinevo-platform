package com.infinevo.payroll.tds.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Request payload for officer PUT /api/v1/payroll/employees/{id}/tds/{fy} (W-36.1 §4).
 */
public record RecordTdsRequest(
        @JsonProperty("regime") String regime,
        @JsonProperty("annual_gross") BigDecimal annualGross,
        @JsonProperty("annual_taxable_income") BigDecimal annualTaxableIncome,
        @JsonProperty("annual_tax") BigDecimal annualTax,
        @JsonProperty("effective_from_period") String effectiveFromPeriod,
        @JsonProperty("note") String note) {}
