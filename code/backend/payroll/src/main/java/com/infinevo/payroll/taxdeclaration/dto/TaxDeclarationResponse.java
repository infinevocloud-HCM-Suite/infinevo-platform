package com.infinevo.payroll.taxdeclaration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Declaration header (W-32.1).
 *
 * <p>{@code pan_required_for_rent_over_threshold} and {@code rent_pan_threshold} are the rent PAN rule the
 * house-rent save enforces (W-32.2), read from the same window flag and {@code reference.hra_rule_master}
 * row, so the screen (W-47.3) never hard-codes it. {@code rent_pan_threshold} is null when no rule row exists.
 */
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
        boolean editable,
        @JsonProperty("pan_required_for_rent_over_threshold") boolean panRequiredForRentOverThreshold,
        @JsonProperty("rent_pan_threshold") BigDecimal rentPanThreshold) {}
