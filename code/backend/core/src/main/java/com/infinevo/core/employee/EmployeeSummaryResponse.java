package com.infinevo.core.employee;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Summary view of an employee returned by search and listing (W-13.3, spec section 4).
 */
public record EmployeeSummaryResponse(
        UUID id,
        String employeeNumber,
        String firstName,
        String middleName,
        String lastName,
        String workEmail,
        String mobile,
        EmploymentStatus status,
        LocalDate dateOfJoining,
        UUID departmentId,
        UUID designationId,
        UUID workLocationId,
        boolean portalEnabled,
        UUID userAccountId,
        boolean isDeleted) {

    public static EmployeeSummaryResponse from(Employee employee) {
        return new EmployeeSummaryResponse(
                employee.getId(),
                employee.getEmployeeNumber(),
                employee.getFirstName(),
                employee.getMiddleName(),
                employee.getLastName(),
                employee.getWorkEmail(),
                employee.getMobile(),
                employee.getStatus(),
                employee.getDateOfJoining(),
                employee.getDepartment() == null
                        ? null
                        : employee.getDepartment().getId(),
                employee.getDesignation() == null
                        ? null
                        : employee.getDesignation().getId(),
                employee.getWorkLocation() == null
                        ? null
                        : employee.getWorkLocation().getId(),
                employee.isPortalEnabled(),
                employee.getUserAccountId(),
                employee.isDeleted());
    }
}
