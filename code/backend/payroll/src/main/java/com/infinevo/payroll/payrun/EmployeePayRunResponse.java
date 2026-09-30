package com.infinevo.payroll.payrun;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * One employee row of a run (W-29.1 §4). {@code employee_number} is read from {@code core.employee}
 * through {@code EmployeeService}, not stored on the row (§13 decision 7); it is null only when the
 * employee has since been soft-deleted.
 */
public record EmployeePayRunResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("employee_number") String employeeNumber,
        @JsonProperty("inclusion_status") InclusionStatus inclusionStatus,
        @JsonProperty("skip_reason") SkipReason skipReason,
        @JsonProperty("salary_version_id") UUID salaryVersionId) {

    public static EmployeePayRunResponse from(EmployeePayRun row, String employeeNumber) {
        return new EmployeePayRunResponse(
                row.getId(),
                row.getEmployeeId(),
                employeeNumber,
                row.getInclusionStatus(),
                row.getSkipReason(),
                row.getSalaryVersionId());
    }
}
