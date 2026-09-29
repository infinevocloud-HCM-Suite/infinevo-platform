package com.infinevo.payroll.statutory.pt;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Request payload to set or update a per-tenant professional tax override (W-31.2).
 */
public record PtOverrideRequest(
        @JsonProperty("registration_number") @JsonAlias("registrationNumber") String registrationNumber,
        @JsonProperty("effective_from") @JsonAlias("effectiveFrom") LocalDate effectiveFrom,
        @JsonProperty("slabs") List<PtSlabDto> slabs) {}
