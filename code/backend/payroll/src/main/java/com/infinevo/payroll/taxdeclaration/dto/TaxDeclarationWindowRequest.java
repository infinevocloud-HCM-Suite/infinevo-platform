package com.infinevo.payroll.taxdeclaration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * The per-year window settings an officer writes (W-32.1), plus the five proof-of-investment
 * settings (W-34.1). A {@code null} proof field leaves the stored value as it is, so a caller that
 * only manages the declaration window never has to know about the proof fields.
 */
public record TaxDeclarationWindowRequest(
        @JsonProperty("window_opens_on") LocalDate windowOpensOn,
        @JsonProperty("window_closes_on") LocalDate windowClosesOn,
        @JsonProperty("is_locked") Boolean isLocked,
        @JsonProperty("default_tax_regime") String defaultTaxRegime,
        @JsonProperty("can_change_tax_regime") Boolean canChangeTaxRegime,
        @JsonProperty("pan_required_for_rent_over_threshold") Boolean panRequiredForRentOverThreshold,
        @JsonProperty("notify_on_lock") Boolean notifyOnLock,
        @JsonProperty("notify_on_release") Boolean notifyOnRelease,
        @JsonProperty("poi_opens_on") LocalDate poiOpensOn,
        @JsonProperty("poi_due_date") LocalDate poiDueDate,
        @JsonProperty("poi_locked") Boolean poiLocked,
        @JsonProperty("poi_attachment_mandatory") Boolean poiAttachmentMandatory,
        @JsonProperty("poi_comment_mandatory") Boolean poiCommentMandatory) {

    /** The W-32.1 shape: the proof settings are left as they are. */
    public TaxDeclarationWindowRequest(
            LocalDate windowOpensOn,
            LocalDate windowClosesOn,
            Boolean isLocked,
            String defaultTaxRegime,
            Boolean canChangeTaxRegime,
            Boolean panRequiredForRentOverThreshold,
            Boolean notifyOnLock,
            Boolean notifyOnRelease) {
        this(
                windowOpensOn,
                windowClosesOn,
                isLocked,
                defaultTaxRegime,
                canChangeTaxRegime,
                panRequiredForRentOverThreshold,
                notifyOnLock,
                notifyOnRelease,
                null,
                null,
                null,
                null,
                null);
    }
}
