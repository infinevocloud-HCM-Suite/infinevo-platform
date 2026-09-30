package com.infinevo.payroll.reimbursement;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request payload for submitting an employee reimbursement claim (W-35.1).
 */
public record ReimbursementClaimRequest(
        @JsonProperty("reimbursement_id") UUID reimbursementId,
        @JsonProperty("requested_amount") BigDecimal requestedAmount,
        @JsonProperty("bill_date") LocalDate billDate,
        @JsonProperty("description") String description,
        @JsonProperty("document_id") UUID documentId) {}
