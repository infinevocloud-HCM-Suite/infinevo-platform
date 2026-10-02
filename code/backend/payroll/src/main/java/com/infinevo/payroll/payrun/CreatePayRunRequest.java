package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code POST /api/v1/payroll/payruns} — the period only, as {@code YYYY-MM}. No status, no dates,
 * no counts: the server derives every other field (W-29.1 §9, "status set from the request body").
 */
public record CreatePayRunRequest(@JsonProperty("period") String period) {}
