package com.infinevo.payroll.taxdeclaration.deductions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxdeclaration.deductions.EnteredBy;
import com.infinevo.payroll.taxdeclaration.deductions.PrevEmploymentKind;
import java.math.BigDecimal;
import java.util.UUID;

public record PrevEmploymentResponse(
        UUID id,
        @JsonProperty("kind") PrevEmploymentKind kind,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("employer_name") String employerName,
        @JsonProperty("employer_tan") String employerTan,
        @JsonProperty("entered_by") EnteredBy enteredBy) {}
