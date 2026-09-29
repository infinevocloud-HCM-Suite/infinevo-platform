package com.infinevo.core.payinput;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code POST /api/v1/pay-inputs/{id}/reverse} request body (W-19 §4). */
public record PayInputReverseRequest(@JsonProperty("reason") String reason) {}
