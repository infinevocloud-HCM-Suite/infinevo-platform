package com.infinevo.payroll.challan;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request payload to allocate challan funds to pay run TDS lines (W-89.1).
 */
public record ChallanAllocationRequest(UUID payRunId, List<EmployeeAllocationItem> employeeAllocations) {

    public record EmployeeAllocationItem(UUID employeeId, BigDecimal taxAmount, BigDecimal cessAmount) {}
}
