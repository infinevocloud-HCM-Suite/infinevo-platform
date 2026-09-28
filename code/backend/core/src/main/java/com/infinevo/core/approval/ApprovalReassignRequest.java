package com.infinevo.core.approval;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * Request payload to reassign a pending approval step to a named employee (W-15.3, spec section 4).
 */
public record ApprovalReassignRequest(
        @JsonProperty("employeeId") UUID employeeId, @JsonProperty("reason") String reason) {}
