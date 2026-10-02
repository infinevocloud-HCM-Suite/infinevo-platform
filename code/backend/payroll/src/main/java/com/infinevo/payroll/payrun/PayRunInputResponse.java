package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * The outcome of one item of {@code POST /payruns/{id}/inputs} (W-30.2 §4), in request order:
 * {@code RECORDED} with the ledger row's id, or {@code DUPLICATE} when the item's reference was
 * already recorded for this run — a retried request writes nothing twice.
 */
public record PayRunInputResponse(
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("source_ref") String sourceRef,
        @JsonProperty("result") Result result,
        @JsonProperty("pay_input_id") UUID payInputId) {

    public enum Result {
        RECORDED,
        DUPLICATE
    }

    static PayRunInputResponse recorded(UUID employeeId, String sourceRef, UUID payInputId) {
        return new PayRunInputResponse(employeeId, sourceRef, Result.RECORDED, payInputId);
    }

    static PayRunInputResponse duplicate(UUID employeeId, String sourceRef) {
        return new PayRunInputResponse(employeeId, sourceRef, Result.DUPLICATE, null);
    }
}
