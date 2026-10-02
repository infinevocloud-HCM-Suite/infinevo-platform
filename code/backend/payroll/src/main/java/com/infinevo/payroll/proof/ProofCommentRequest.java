package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for posting a comment on a proof item (W-34.2 spec §4).
 *
 * @param body comment text (1 to 1000 characters)
 */
public record ProofCommentRequest(@JsonProperty("body") String body) {}
