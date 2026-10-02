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
        @JsonProperty("is_open") boolean isOpen,
        @JsonProperty("poi_opens_on") LocalDate poiOpensOn,
        @JsonProperty("poi_due_date") LocalDate poiDueDate,
        @JsonProperty("poi_locked") boolean poiLocked,
        @JsonProperty("poi_attachment_mandatory") boolean poiAttachmentMandatory,
        @JsonProperty("poi_comment_mandatory") boolean poiCommentMandatory,
        @JsonProperty("poi_is_open") boolean poiIsOpen) {

    /** The W-32.1 shape: no proof window set, attachments mandatory, comments not. */
    public TaxDeclarationWindowResponse(
            UUID id,
            String financialYear,
            LocalDate windowOpensOn,
            LocalDate windowClosesOn,
            boolean isLocked,
            String defaultTaxRegime,
            boolean canChangeTaxRegime,
            boolean panRequiredForRentOverThreshold,
            boolean notifyOnLock,
            boolean notifyOnRelease,
            boolean exists,
            boolean isOpen) {
        this(
                id,
                financialYear,
                windowOpensOn,
                windowClosesOn,
                isLocked,
                defaultTaxRegime,
                canChangeTaxRegime,
                panRequiredForRentOverThreshold,
                notifyOnLock,
                notifyOnRelease,
                exists,
                isOpen,
                null,
                null,
                false,
                true,
                false,
                false);
    }
}
