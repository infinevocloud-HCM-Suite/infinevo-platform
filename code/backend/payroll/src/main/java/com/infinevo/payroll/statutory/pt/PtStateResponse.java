package com.infinevo.payroll.statutory.pt;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Response for a state's professional tax configuration (W-31.2).
 */
public record PtStateResponse(
        @JsonProperty("state_code") @JsonAlias("stateCode") String stateCode,
        @JsonProperty("state_name") @JsonAlias("stateName") String stateName,
        @JsonProperty("source") PtSource source,
        @JsonProperty("registration_number") @JsonAlias("registrationNumber") String registrationNumber,
        @JsonProperty("effective_from") @JsonAlias("effectiveFrom") LocalDate effectiveFrom,
        @JsonProperty("slabs") List<PtSlabDto> slabs) {}
