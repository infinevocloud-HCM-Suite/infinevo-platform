package com.infinevo.payroll.statutory.pt;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Historical record of a professional tax override change (W-31.2).
 */
public record PtHistoryResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("state_code") @JsonAlias("stateCode") String stateCode,
        @JsonProperty("operation") PtHistoryOperation operation,
        @JsonProperty("before_slabs") @JsonAlias("beforeSlabs") List<PtSlabDto> beforeSlabs,
        @JsonProperty("after_slabs") @JsonAlias("afterSlabs") List<PtSlabDto> afterSlabs,
        @JsonProperty("changed_at") @JsonAlias("changedAt") Instant changedAt,
        @JsonProperty("changed_by") @JsonAlias("changedBy") UUID changedBy) {}
