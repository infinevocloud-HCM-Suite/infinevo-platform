package com.infinevo.payroll.taxdeclaration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import java.time.Instant;
import java.util.UUID;

public record TaxDeclarationResponse(
        UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("tax_regime") String taxRegime,
        DeclarationStatus status,
        @JsonProperty("is_staying_in_rented_house") boolean isStayingInRentedHouse,
        @JsonProperty("is_repaying_self_occupied_loan") boolean isRepayingSelfOccupiedLoan,
        @JsonProperty("has_let_out_property") boolean hasLetOutProperty,
        @JsonProperty("is_locked") boolean isLocked,
        @JsonProperty("submitted_at") Instant submittedAt,
        @JsonProperty("locked_at") Instant lockedAt,
        @JsonProperty("window_open") boolean windowOpen,
        boolean editable) {}
