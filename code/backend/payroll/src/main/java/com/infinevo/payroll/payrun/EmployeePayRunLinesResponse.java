package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /payruns/{id}/employees/{employeeId}/lines} (W-29.2 §4): the lines in {@code sort_order},
 * and the row's {@code computation_error} when the employee could not be computed.
 */
public record EmployeePayRunLinesResponse(
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("computation_error") String computationError,
        @JsonProperty("lines") List<EmployeePayRunLineResponse> lines) {}
