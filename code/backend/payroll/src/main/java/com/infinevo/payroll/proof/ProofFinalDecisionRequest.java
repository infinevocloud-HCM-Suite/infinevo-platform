package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for the final proof decision (W-34.2 spec §4).
 *
 * @param action {@code APPROVE} or {@code RETURN}
 * @param comment justification comment (required for {@code RETURN}, or when mandatory setting is enabled)
 */
public record ProofFinalDecisionRequest(
        @JsonProperty("action") ProofFinalDecisionAction action, @JsonProperty("comment") String comment) {}
