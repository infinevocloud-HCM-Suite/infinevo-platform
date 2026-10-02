package com.infinevo.payroll.deduction;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** The answer to a batch (W-35.2 §4): every row written, in the order of the request's lines. */
public record EmployeeDeductionBatchResponse(
        @JsonProperty("count") int count, @JsonProperty("rows") List<EmployeeDeductionResponse> rows) {

    static EmployeeDeductionBatchResponse of(List<EmployeeDeductionResponse> rows) {
        return new EmployeeDeductionBatchResponse(rows.size(), List.copyOf(rows));
    }
}
