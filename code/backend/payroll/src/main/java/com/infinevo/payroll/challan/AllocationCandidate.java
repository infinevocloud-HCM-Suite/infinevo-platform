package com.infinevo.payroll.challan;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An employee with TDS on an approved or paid run of the challan's quarter (W-89.1): the TDS deducted, how much
 * other challans already cover, how much this challan covers, and what is left to cover.
 */
public record AllocationCandidate(
        UUID payRunId,
        String period,
        String runType,
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        BigDecimal tdsDeducted,
        BigDecimal allocatedElsewhere,
        BigDecimal allocatedHere,
        BigDecimal remaining) {}
