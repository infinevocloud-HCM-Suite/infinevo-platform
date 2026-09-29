package com.infinevo.payroll.taxdeclaration.deductions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxdeclaration.deductions.PrevEmploymentKind;
import java.math.BigDecimal;

public record PrevEmploymentRequest(
        @JsonProperty("kind") PrevEmploymentKind kind,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("employer_name") String employerName,
        @JsonProperty("employer_tan") String employerTan) {}
