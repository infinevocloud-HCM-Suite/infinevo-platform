package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row in the proof-of-investment chase list (W-34.3 spec section 4).
 *
 * @param employeeId ID of the employee
 * @param number employee payroll number
 * @param name full name of the employee
 * @param taxRegime tax regime on the declaration (e.g. NEW / OLD)
 * @param proofStatus reported status: NOT_STARTED, DRAFT, SUBMITTED, APPROVED, or REJECTED
 * @param proofId ID of the proof record, or null if NOT_STARTED
 * @param submittedAt timestamp when proof was submitted, or null
 * @param claimedTotal sum of positive claimed amounts on the proof
 * @param approvedTotal sum of approved amounts on the proof, or null if not approved
 */
public record ProofChaseRow(
        @JsonProperty("employee_id") UUID employeeId,
        @JsonAlias("employee_number") @JsonProperty("number") String number,
        @JsonAlias("employee_name") @JsonProperty("name") String name,
        @JsonProperty("tax_regime") String taxRegime,
        @JsonProperty("proof_status") String proofStatus,
        @JsonProperty("proof_id") UUID proofId,
        @JsonProperty("submitted_at") Instant submittedAt,
        @JsonProperty("claimed_total") BigDecimal claimedTotal,
        @JsonProperty("approved_total") BigDecimal approvedTotal) {}
