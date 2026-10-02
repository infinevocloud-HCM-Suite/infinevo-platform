package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/** What the employee sets on one item: the amount they claim and an optional note (W-34.1). */
public record ProofItemUpdateRequest(
        @JsonProperty("claimed_amount") BigDecimal claimedAmount, @JsonProperty("employee_note") String employeeNote) {}
