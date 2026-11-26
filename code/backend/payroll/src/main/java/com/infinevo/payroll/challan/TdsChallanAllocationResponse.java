package com.infinevo.payroll.challan;

import java.math.BigDecimal;
import java.util.UUID;

/** One allocation row of a challan, with the run's month and the employee's number and name (W-89.1). */
public record TdsChallanAllocationResponse(
        UUID id,
        UUID challanId,
        UUID payRunId,
        String period,
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        BigDecimal allocatedTax,
        BigDecimal allocatedSurcharge,
        BigDecimal allocatedCess,
        BigDecimal total) {}
