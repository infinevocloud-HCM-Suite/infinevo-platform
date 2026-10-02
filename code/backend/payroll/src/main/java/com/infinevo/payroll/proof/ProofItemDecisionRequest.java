package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Request payload for deciding an individual proof item (W-34.2 spec §4).
 *
 * @param action {@code APPROVE}, {@code DISALLOW}, or {@code RETURN}
 * @param approvedAmount approved monetary amount (required for {@code APPROVE})
 * @param comment optional or mandatory justification comment depending on action and settings
 */
public record ProofItemDecisionRequest(
        @JsonProperty("action") ProofItemDecisionAction action,
        @JsonProperty("approved_amount") BigDecimal approvedAmount,
        @JsonProperty("comment") String comment) {}
