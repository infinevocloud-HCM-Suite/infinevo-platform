package com.infinevo.payroll.challan;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Sets how much of a challan covers each employee's TDS on one pay run (W-89.1). Each item replaces that
 * employee's amounts on this challan and run; an item of all zeros removes it.
 */
public record ChallanAllocationRequest(UUID payRunId, List<EmployeeAllocationItem> employeeAllocations) {

    public record EmployeeAllocationItem(
            UUID employeeId, BigDecimal taxAmount, BigDecimal surchargeAmount, BigDecimal cessAmount) {}
}
