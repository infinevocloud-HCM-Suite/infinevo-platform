package com.infinevo.hrms.exit;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Hand-off payload emitted to Payroll F&F settlement upon clearance completion (W-103 §5a).
 */
public record PayrollHandOffPayload(
        UUID employeeId,
        LocalDate resignationDate,
        LocalDate exitDate,
        String exitReason,
        int workingDaysInExitMonth,
        ExitStatus exitStatus) {}
