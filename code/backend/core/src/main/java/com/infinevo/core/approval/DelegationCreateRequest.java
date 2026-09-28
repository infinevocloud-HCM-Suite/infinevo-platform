package com.infinevo.core.approval;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload to create an approval delegation (W-15.3, spec section 4).
 */
public record DelegationCreateRequest(
        @JsonProperty("delegateId") UUID delegateId,
        @JsonAlias("effectiveFrom") @JsonProperty("from") LocalDate from,
        @JsonAlias("effectiveTo") @JsonProperty("to") LocalDate to,
        @JsonProperty("flowTypes") String flowTypes) {}
