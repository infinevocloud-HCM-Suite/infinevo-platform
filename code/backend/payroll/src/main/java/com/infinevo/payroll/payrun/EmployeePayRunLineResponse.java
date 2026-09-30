package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

/** One pay line as the API returns it (W-29.2 §4) — the component as it was named at compute time. */
public record EmployeePayRunLineResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("line_kind") LineKind lineKind,
        @JsonProperty("source") LineSource source,
        @JsonProperty("component_id") UUID componentId,
        @JsonProperty("component_code") String componentCode,
        @JsonProperty("component_name") String componentName,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("is_taxable") boolean taxable,
        @JsonProperty("sort_order") int sortOrder) {

    public static EmployeePayRunLineResponse from(EmployeePayRunLine line) {
        return new EmployeePayRunLineResponse(
                line.getId(),
                line.getLineKind(),
                line.getSource(),
                line.getComponentId(),
                line.getComponentCode(),
                line.getComponentName(),
                line.getAmount(),
                line.isTaxable(),
                line.getSortOrder());
    }
}
