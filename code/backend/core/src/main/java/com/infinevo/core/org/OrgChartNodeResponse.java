package com.infinevo.core.org;

import com.infinevo.core.employee.Employee;
import java.util.List;
import java.util.UUID;

/**
 * Node in the organizational chart hierarchy (W-14.2, spec section 4).
 */
public record OrgChartNodeResponse(
        UUID employeeId,
        String employeeNumber,
        String firstName,
        String lastName,
        String workEmail,
        UUID departmentId,
        UUID designationId,
        List<OrgChartNodeResponse> directReports) {

    public static OrgChartNodeResponse from(Employee employee, List<OrgChartNodeResponse> directReports) {
        return new OrgChartNodeResponse(
                employee.getId(),
                employee.getEmployeeNumber(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getWorkEmail(),
                employee.getDepartment() == null
                        ? null
                        : employee.getDepartment().getId(),
                employee.getDesignation() == null
                        ? null
                        : employee.getDesignation().getId(),
                directReports != null ? directReports : List.of());
    }
}
