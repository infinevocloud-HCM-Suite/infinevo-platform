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
        @JsonProperty("signatory_designation") @JsonAlias("signatoryDesignation") String signatoryDesignation) {}
