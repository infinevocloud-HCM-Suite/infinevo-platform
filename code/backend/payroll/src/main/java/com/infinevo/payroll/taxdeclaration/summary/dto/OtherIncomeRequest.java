package com.infinevo.payroll.taxdeclaration.summary.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxdeclaration.summary.OtherIncomeKind;
import java.math.BigDecimal;

public record OtherIncomeRequest(
        @JsonProperty("kind") OtherIncomeKind kind,
        @JsonProperty("description") String description,
        @JsonProperty("amount") BigDecimal amount) {}
