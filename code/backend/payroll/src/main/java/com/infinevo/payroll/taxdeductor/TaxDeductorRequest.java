package com.infinevo.payroll.taxdeductor;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Request payload for saving tax deductor details (W-36.3).
 */
public record TaxDeductorRequest(
        @JsonProperty("tan") @JsonAlias("tan") String tan,
        @JsonProperty("pan") @JsonAlias("pan") String pan,
        @JsonProperty("tds_circle") @JsonAlias("tdsCircle") String tdsCircle,
        @JsonProperty("signatory_employee_id") @JsonAlias("signatoryEmployeeId") UUID signatoryEmployeeId,
        @JsonProperty("signatory_name") @JsonAlias("signatoryName") String signatoryName,
        @JsonProperty("signatory_parent_name") @JsonAlias("signatoryParentName") String signatoryParentName,
        @JsonProperty("signatory_designation") @JsonAlias("signatoryDesignation") String signatoryDesignation,
        @JsonProperty("deductor_type") @JsonAlias("deductorType") String deductorType,
        @JsonProperty("email") @JsonAlias("email") String email,
        @JsonProperty("phone") @JsonAlias("phone") String phone,
        @JsonProperty("gstin") @JsonAlias("gstin") String gstin,
        @JsonProperty("responsible_pan") @JsonAlias("responsiblePan") String responsiblePan,
        @JsonProperty("responsible_email") @JsonAlias("responsibleEmail") String responsibleEmail,
        @JsonProperty("responsible_phone") @JsonAlias("responsiblePhone") String responsiblePhone) {

    /** Form 16's fields only; the quarterly-return fields stay empty (W-36.3 callers). */
    public TaxDeductorRequest(
            String tan,
            String pan,
            String tdsCircle,
            UUID signatoryEmployeeId,
            String signatoryName,
            String signatoryParentName,
            String signatoryDesignation) {
        this(
                tan,
                pan,
                tdsCircle,
                signatoryEmployeeId,
                signatoryName,
                signatoryParentName,
                signatoryDesignation,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
