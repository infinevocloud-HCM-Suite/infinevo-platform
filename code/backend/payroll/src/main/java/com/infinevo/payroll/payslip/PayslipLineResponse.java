package com.infinevo.payroll.payslip;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * One line on a payslip (W-36.2 §4).
 */
public record PayslipLineResponse(
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("source") String source,
        @JsonProperty("is_taxable") boolean isTaxable) {}
