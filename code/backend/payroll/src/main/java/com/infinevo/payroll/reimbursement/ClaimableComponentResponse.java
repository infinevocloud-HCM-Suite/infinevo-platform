package com.infinevo.payroll.reimbursement;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.infinevo.payroll.component.Reimbursement;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A reimbursement component an employee may claim against (W-47.4 §4): an active, undeleted
 * {@code payroll.reimbursement} row of the bound tenant. {@code max_limit} is advisory — a claim above it
 * is still accepted (W-35.1 §4).
 */
public record ClaimableComponentResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("code") String code,
        @JsonProperty("name") String name,
        @JsonProperty("max_limit") BigDecimal maxLimit) {

    static ClaimableComponentResponse from(Reimbursement component) {
        return new ClaimableComponentResponse(
                component.getId(), component.getCode(), component.getName(), component.getMaxLimit());
    }
}
