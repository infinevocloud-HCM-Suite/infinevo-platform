package com.infinevo.payroll.taxdeclaration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

public record TaxDeclarationWindowResponse(
        UUID id,
        @JsonProperty("financial_year") String financialYear,
        @JsonProperty("window_opens_on") LocalDate windowOpensOn,
        @JsonProperty("window_closes_on") LocalDate windowClosesOn,
        @JsonProperty("is_locked") boolean isLocked,
        @JsonProperty("default_tax_regime") String defaultTaxRegime,
        @JsonProperty("can_change_tax_regime") boolean canChangeTaxRegime,
        @JsonProperty("pan_required_for_rent_over_threshold") boolean panRequiredForRentOverThreshold,
        @JsonProperty("notify_on_lock") boolean notifyOnLock,
        @JsonProperty("notify_on_release") boolean notifyOnRelease,
        boolean exists,
        @JsonProperty("is_open") boolean isOpen) {}
