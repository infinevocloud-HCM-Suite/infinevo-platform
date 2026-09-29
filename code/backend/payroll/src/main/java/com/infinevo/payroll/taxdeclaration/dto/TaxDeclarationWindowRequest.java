package com.infinevo.payroll.taxdeclaration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record TaxDeclarationWindowRequest(
        @JsonProperty("window_opens_on") LocalDate windowOpensOn,
        @JsonProperty("window_closes_on") LocalDate windowClosesOn,
        @JsonProperty("is_locked") Boolean isLocked,
        @JsonProperty("default_tax_regime") String defaultTaxRegime,
        @JsonProperty("can_change_tax_regime") Boolean canChangeTaxRegime,
        @JsonProperty("pan_required_for_rent_over_threshold") Boolean panRequiredForRentOverThreshold,
        @JsonProperty("notify_on_lock") Boolean notifyOnLock,
        @JsonProperty("notify_on_release") Boolean notifyOnRelease) {}
