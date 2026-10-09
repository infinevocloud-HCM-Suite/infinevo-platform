package com.infinevo.core.employeeimport;

/**
 * One data row of the import file, as written: trimmed, blanks as {@code null}, nothing interpreted
 * (W-73.7 §2). {@link EmployeeImportParser} reads it; the service decides whether it makes an employee.
 *
 * @param rowNumber 1 for the first line under the header
 */
public record EmployeeImportRow(
        int rowNumber,
        String employeeNumber,
        String firstName,
        String lastName,
        String workEmail,
        String mobile,
        String dateOfJoining,
        String department,
        String designation,
        String location,
        String giveAccess,
        String roles) {}
