package com.infinevo.payroll.taxdeclaration.summary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record ComputedTaxSummaryResponse(
        @JsonProperty("taxable_income") BigDecimal taxableIncome,
        @JsonProperty("net_taxable_income") BigDecimal netTaxableIncome,
        @JsonProperty("tax_on_taxable_income") BigDecimal taxOnTaxableIncome,
        @JsonProperty("tax_ytd_amount") BigDecimal taxYtdAmount,
        @JsonProperty("tax_to_be_paid") BigDecimal taxToBePaid,
        @JsonProperty("tds_through_payroll") BigDecimal tdsThroughPayroll,
        @JsonProperty("tds_previous_employer") BigDecimal tdsPreviousEmployer,
        @JsonProperty("tds_other_income") BigDecimal tdsOtherIncome,
        @JsonProperty("other_sources_income") BigDecimal otherSourcesIncome,
        @JsonProperty("exemption_under_section10") BigDecimal exemptionUnderSection10,
        @JsonProperty("exemption_under_section6a") BigDecimal exemptionUnderSection6a,
        @JsonProperty("remaining_months") Integer remainingMonths,
        @JsonProperty("computed_at") Instant computedAt) {}
