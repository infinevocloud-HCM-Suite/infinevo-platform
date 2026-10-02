package com.infinevo.core.leave;

/**
 * Record representing a validation or processing error for a specific row in a bulk import (W-16.4b).
 *
 * @param lineNumber 1-indexed line number in source CSV
 * @param employeeNumber employee identifier from row
 * @param leaveTypeCode leave type code from row
 * @param days raw days value from row
 * @param reason reason code explaining failure
 */
public record LeaveImportError(
        int lineNumber, String employeeNumber, String leaveTypeCode, String days, String reason) {}
