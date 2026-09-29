package com.infinevo.core.payinput;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * {@code POST /api/v1/pay-inputs} request body (W-19 §4). {@code amount} is plain
 * {@code BigDecimal} here — JSON has no {@code Money} — and is wrapped at the controller boundary
 * before it reaches {@link PayInputCommand}.
 */
public record PayInputRequest(
        @JsonProperty("employee_id") @JsonAlias("employeeId") UUID employeeId,
        @JsonProperty("period") YearMonth period,
        @JsonProperty("kind") PayInputKind kind,
        @JsonProperty("quantity") BigDecimal quantity,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("source_ref") @JsonAlias("sourceRef") String sourceRef,
        @JsonProperty("run_ref") @JsonAlias("runRef") UUID runRef) {}
