package com.infinevo.payroll.taxdeclaration.housing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxdeclaration.housing.LetOutPropertyLineType;
import java.math.BigDecimal;

public record LetOutPropertyLineRequest(
        @JsonProperty("line_type") LetOutPropertyLineType lineType,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("lender_name") String lenderName,
        @JsonProperty("lender_pan") String lenderPan) {}
