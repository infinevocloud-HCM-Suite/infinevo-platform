package com.infinevo.core.payinput;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * One {@code core.pay_input} row (W-19 §4). {@code postedPeriod} is named for what it is — the
 * period the row actually landed in, which is the requested one unless it was locked — so a caller
 * comparing it against what it asked for sees the redirect without guessing which field means what.
 */
public record PayInputResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("posted_period") YearMonth postedPeriod,
        @JsonProperty("kind") PayInputKind kind,
        @JsonProperty("quantity") BigDecimal quantity,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("source_module") String sourceModule,
        @JsonProperty("source_ref") String sourceRef,
        @JsonProperty("run_ref") UUID runRef,
        @JsonProperty("reverses_id") UUID reversesId,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("note") String note) {

    /** The shape before D-140, with no {@code createdBy} and no {@code note}. Kept for existing callers. */
    public PayInputResponse(
            UUID id,
            UUID employeeId,
            YearMonth postedPeriod,
            PayInputKind kind,
            BigDecimal quantity,
            BigDecimal amount,
            String sourceModule,
            String sourceRef,
            UUID runRef,
            UUID reversesId,
            Instant createdAt) {
        this(
                id,
                employeeId,
                postedPeriod,
                kind,
                quantity,
                amount,
                sourceModule,
                sourceRef,
                runRef,
                reversesId,
                createdAt,
                null,
                null);
    }

    static PayInputResponse from(PayInput entity) {
        return new PayInputResponse(
                entity.getId(),
                entity.getEmployeeId(),
                entity.getPeriod(),
                entity.getKind(),
                entity.getQuantity(),
                entity.getAmount(),
                entity.getSourceModule(),
                entity.getSourceRef(),
                entity.getRunRef(),
                entity.getReversesId(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getNote());
    }
}
