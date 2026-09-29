package com.infinevo.payroll.taxdeclaration.deductions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DeductionDeclarationResponse(
        @JsonProperty("section6a") List<Section6ALineResponse> section6a,
        @JsonProperty("pre_tax_deductions") List<PreTaxDeductionResponse> preTaxDeductions,
        @JsonProperty("previous_employment") List<PrevEmploymentResponse> previousEmployment) {}
