package com.infinevo.payroll.taxdeclaration.deductions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxdeclaration.deductions.PreTaxDeductionKind;
import java.math.BigDecimal;

public record PreTaxDeductionRequest(
        @JsonProperty("kind") PreTaxDeductionKind kind, @JsonProperty("amount") BigDecimal amount) {}
