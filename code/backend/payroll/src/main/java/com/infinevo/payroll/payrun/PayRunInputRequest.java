package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.core.payinput.PayInputKind;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One item of {@code POST /api/v1/payroll/payruns/{id}/inputs} (W-30.2 §4): an amount, never days —
 * legacy's ÷30 pricing is dropped (DEBT-036). {@code sourceRef} is the officer's own retry key; the
 * run prefixes it with its id, so the same reference on two runs is two rows.
 */
public record PayRunInputRequest(
        @JsonProperty("employee_id") @JsonAlias("employeeId") UUID employeeId,
        @JsonProperty("kind") PayInputKind kind,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("source_ref") @JsonAlias("sourceRef") String sourceRef) {}
