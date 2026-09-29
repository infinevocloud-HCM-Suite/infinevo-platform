package com.infinevo.payroll.taxdeclaration.summary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;

public record TaxSummaryResponse(
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("tax_regime") String taxRegime,
        @JsonProperty("status") DeclarationStatus status,
        @JsonProperty("declared") DeclaredTotals declared,
        @JsonProperty("computed") ComputedTaxSummaryResponse computed) {}
