package com.infinevo.payroll.payrun.bankfile;

import java.util.List;
import java.util.UUID;

/**
 * Preview or make a bank file (W-88.2). {@code employeeIds} empty or null = everyone the rules allow.
 */
public record BankFileExportRequest(String templateId, List<UUID> employeeIds) {}
