package com.infinevo.payroll.taxcalc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Breakdown of tax computed for an individual statutory slab bracket (W-33.1).
 */
public record SlabLineResponse(
        @JsonProperty("from_amount") BigDecimal fromAmount,
        @JsonProperty("to_amount") BigDecimal toAmount,
        @JsonProperty("rate") BigDecimal rate,
        @JsonProperty("taxable_amount") BigDecimal taxableAmount,
        @JsonProperty("tax_amount") BigDecimal taxAmount) {}
