package com.infinevo.core.leave;

/**
 * Parsed row from a bulk leave allocation CSV file (W-16.4b, spec section 4).
 *
 * @param lineNumber 1-indexed source line number in the CSV file
 * @param employeeNumber the employee identifier in the tenant
 * @param leaveTypeCode the leave type code (e.g. "SL", "CL", "AL")
 * @param days raw day count string as parsed from the file
 */
public record LeaveImportRow(int lineNumber, String employeeNumber, String leaveTypeCode, String days) {}
