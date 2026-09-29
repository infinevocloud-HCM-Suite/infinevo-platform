package com.infinevo.payroll.taxdeclaration.housing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record HomeLoanResponse(
        UUID id,
        @JsonProperty("lender_name") String lenderName,
        @JsonProperty("lender_pan") String lenderPan,
        @JsonProperty("principal_paid") BigDecimal principalPaid,
        @JsonProperty("interest_paid") BigDecimal interestPaid,
        @JsonProperty("is_first_time_buyer") boolean isFirstTimeBuyer,
        @JsonProperty("loan_sanctioned_on") LocalDate loanSanctionedOn) {}
