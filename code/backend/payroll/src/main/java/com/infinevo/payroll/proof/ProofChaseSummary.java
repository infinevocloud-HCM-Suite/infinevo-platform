package com.infinevo.payroll.proof;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.Map;

/**
 * Summary counts and window state for proof-of-investment chase (W-34.3 spec section 4).
 *
 * @param notStarted employees with submitted declaration but no proof row
 * @param draft proofs currently in DRAFT status
 * @param submitted proofs awaiting verification
 * @param approved proofs verified and approved
 * @param rejected proofs returned/rejected
 * @param dueDate proof-of-investment submission due date from declaration window
 * @param proofOpen whether the proof window is currently open
 */
public record ProofChaseSummary(
        @JsonProperty("not_started") long notStarted,
        @JsonProperty("draft") long draft,
        @JsonProperty("submitted") long submitted,
        @JsonProperty("approved") long approved,
        @JsonProperty("rejected") long rejected,
        @JsonProperty("due_date") LocalDate dueDate,
        @JsonProperty("proof_open") boolean proofOpen) {

    @JsonProperty("counts")
    public Map<String, Long> counts() {
        return Map.of(
                "NOT_STARTED", notStarted,
                "DRAFT", draft,
                "SUBMITTED", submitted,
                "APPROVED", approved,
                "REJECTED", rejected);
    }
}
